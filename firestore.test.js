const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_user_123";
const BOB_UID = "bob_user_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write user profiles or recent docs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      userId: ALICE_UID,
      firstName: "Alice",
      lastName: "Johnson",
      displayName: "Alice Johnson",
      email: "alice@company.org",
      workspaceRole: "Personal",
      provider: "password",
      emailVerified: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("recent_docs").get()
  );
});

test("Email/Password and Google authenticated owners can create, read, update, and delete own UserProfile while passwords are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  // Reject storing any password field in Firestore
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      userId: ALICE_UID,
      firstName: "Alice",
      lastName: "Johnson",
      displayName: "Alice Johnson",
      email: "alice@outlook.com",
      password: "NeverStoreThisPassword1!",
      workspaceRole: "Personal",
      provider: "password",
      emailVerified: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid Kotlin-mirrored Email & Password create payload (any email provider, no password)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      userId: ALICE_UID,
      firstName: "Alice",
      lastName: "Johnson",
      displayName: "Alice Johnson",
      email: "alice@outlook.com",
      workspaceRole: "Business",
      provider: "password",
      emailVerified: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());

  // Valid partial update (firstName, lastName, displayName, workspaceRole, emailVerified, updatedAt)
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      firstName: "Alicia",
      lastName: "J.",
      displayName: "Alicia J.",
      workspaceRole: "Legal & Finance",
      emailVerified: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field / password injection on update
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      password: "InjectedPassword1!",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt or provider modification
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      provider: "google.com",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid workspaceRole enum
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      workspaceRole: "SuperAdmin",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp tampering
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Future Alice",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).delete());

  // Also verify Google provider create succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      userId: ALICE_UID,
      firstName: "Alice",
      lastName: "Johnson",
      displayName: "Alice Johnson",
      email: "alice@gmail.com",
      workspaceRole: "Personal",
      provider: "google.com",
      emailVerified: true,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation: Bob cannot read, modify, or list Alice's profile or recent docs", async () => {
  const now = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      userId: ALICE_UID,
      firstName: "Alice",
      lastName: "Smith",
      displayName: "Alice Smith",
      email: "alice@proton.me",
      workspaceRole: "Personal",
      provider: "password",
      emailVerified: false,
      createdAt: now,
      updatedAt: now,
    });
    await adminDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .doc("doc_1")
      .set({
        userId: ALICE_UID,
        fileName: "Tax_2026.pdf",
        mimeType: "application/pdf",
        sizeBytes: 40960,
        pageCount: 4,
        lastToolId: "sign",
        lastToolName: "Sign PDF",
        summaryNote: "Signed Page 4",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("recent_docs").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .doc("doc_1")
      .delete()
  );
});

test("Authenticated owner can create, list, update, and delete own recent_docs subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("recent_docs").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .doc("doc_abc_1")
      .set({
        userId: ALICE_UID,
        fileName: "Quarterly_Report.pdf",
        mimeType: "application/pdf",
        sizeBytes: 128000,
        pageCount: 6,
        lastToolId: "compress",
        lastToolName: "Compress PDF",
        summaryNote: "Compressed 42% smaller",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .doc("doc_abc_2")
      .set({
        id: "doc_abc_2",
        userId: ALICE_UID,
        fileName: "Bad.pdf",
        mimeType: "application/pdf",
        sizeBytes: 1024,
        pageCount: 1,
        lastToolId: "edit",
        lastToolName: "Edit PDF",
        summaryNote: "Bad payload",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .doc("doc_abc_1")
      .update({
        lastToolId: "sign",
        lastToolName: "Sign PDF",
        summaryNote: "Signed by Alice",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .orderBy("updatedAt", "desc")
      .limit(20)
      .get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("recent_docs")
      .doc("doc_abc_1")
      .delete()
  );
});
