/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2016 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.selfid.olm;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OlmSessionTest {
    private static final String LOG_TAG = "OlmSessionTest";
    private final String INVALID_PRE_KEY = "invalid PRE KEY hu hu!";
    private final String FILE_NAME_SERIAL_SESSION = "SerialSession";
    private final int ONE_TIME_KEYS_NUMBER = 4;

    private static OlmManager mOlmManager;

    @BeforeClass
    public static void setUpClass(){
        // load native lib
        mOlmManager = new OlmManager();

        String version = mOlmManager.getOlmLibVersion();
        assertNotNull(version);
        Log.d(LOG_TAG, "## setUpClass(): lib version="+version);
    }

    /**
     * Basic test:
     * - alice creates an account
     * - bob creates an account
     * - alice creates an outbound session with bob (bobIdentityKey & bobOneTimeKey)
     * - alice encrypts a message with its session
     * - bob creates an inbound session based on alice's encrypted message
     * - bob decrypts the encrypted message with its session
     */
    @Test
    public void test01AliceToBob() {
        final int ONE_TIME_KEYS_NUMBER = 5;
        String bobIdentityKey = null;
        String bobOneTimeKey=null;
        OlmAccount bobAccount = null;
        OlmAccount aliceAccount = null;

        // ALICE & BOB ACCOUNTS CREATION
        try {
            aliceAccount = new OlmAccount();
            bobAccount = new OlmAccount();
        } catch (OlmException e) {
            assertTrue(e.getMessage(),false);
        }

        // test accounts creation
        assertTrue(0!=bobAccount.getOlmAccountId());
        assertTrue(0!=aliceAccount.getOlmAccountId());

        // get bob identity key
        Map<String, String> bobIdentityKeys = null;

        try {
            bobIdentityKeys = bobAccount.identityKeys();
        } catch (Exception e) {
            assertTrue("identityKeys failed " + e.getMessage(), false);
        }

        bobIdentityKey = TestHelper.getIdentityKey(bobIdentityKeys);
        assertTrue(null!=bobIdentityKey);

        // get bob one time keys
        try {
            bobAccount.generateOneTimeKeys(ONE_TIME_KEYS_NUMBER);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        Map<String, Map<String, String>> bobOneTimeKeys = null;

        try {
            bobOneTimeKeys = bobAccount.oneTimeKeys();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        bobOneTimeKey = TestHelper.getOneTimeKey(bobOneTimeKeys,1);
        assertNotNull(bobOneTimeKey);

        // CREATE ALICE SESSION
        OlmSession aliceSession = null;
        try {
            aliceSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }
        assertTrue(0!=aliceSession.getOlmSessionId());

        // CREATE ALICE OUTBOUND SESSION and encrypt message to bob
        try {
            aliceSession.initOutboundSession(aliceAccount, bobIdentityKey, bobOneTimeKey);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        String clearMsg = "Heloo bob , this is alice!";
        OlmMessage encryptedMsgToBob = null;
        try {
            encryptedMsgToBob = aliceSession.encryptMessage(clearMsg);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsgToBob);
        assertNotNull(encryptedMsgToBob.mCipherText);
        Log.d(LOG_TAG,"## test01AliceToBob(): encryptedMsg="+encryptedMsgToBob.mCipherText);

        // CREATE BOB INBOUND SESSION and decrypt message from alice
        OlmSession bobSession = null;
        try {
            bobSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }
        assertTrue(0!=bobSession.getOlmSessionId());

        try {
            bobSession.initInboundSession(bobAccount, encryptedMsgToBob.mCipherText);
        } catch (Exception e) {
            assertTrue("initInboundSessionWithAccount failed " + e.getMessage(), false);
        }

        String decryptedMsg = null;
        try {
            decryptedMsg = bobSession.decryptMessage(encryptedMsgToBob);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(decryptedMsg);

        // MESSAGE COMPARISON: decrypted vs encrypted
        assertTrue(clearMsg.equals(decryptedMsg));

        // clean objects..
        try {
            bobAccount.removeOneTimeKeys(bobSession);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        // release accounts
        bobAccount.releaseAccount();
        aliceAccount.releaseAccount();
        assertTrue(bobAccount.isReleased());
        assertTrue(aliceAccount.isReleased());

        // release sessions
        bobSession.releaseSession();
        aliceSession.releaseSession();
        assertTrue(bobSession.isReleased());
        assertTrue(aliceSession.isReleased());
    }


    /**
     * Same as test01AliceToBob but with bob who's encrypting messages
     * to alice and alice decrypt them.<br>
     * - alice creates an account
     * - bob creates an account
     * - alice creates an outbound session with bob (bobIdentityKey & bobOneTimeKey)
     * - alice encrypts a message with its own session
     * - bob creates an inbound session based on alice's encrypted message
     * - bob decrypts the encrypted message with its own session
     * - bob encrypts messages with its own session
     * - alice decrypts bob's messages with its own message
     * - alice encrypts a message
     * - bob decrypts the encrypted message
     */
    @Test
    public void test02AliceToBobBackAndForth() {
        String bobIdentityKey;
        String bobOneTimeKey;
        OlmAccount aliceAccount = null;
        OlmAccount bobAccount = null;

        // creates alice & bob accounts
        try {
            aliceAccount = new OlmAccount();
            bobAccount = new OlmAccount();
        } catch (OlmException e) {
            assertTrue(e.getMessage(),false);
        }

        // test accounts creation
        assertTrue(0!=bobAccount.getOlmAccountId());
        assertTrue(0!=aliceAccount.getOlmAccountId());

        // get bob identity key
        Map<String, String> bobIdentityKeys = null;

        try {
            bobIdentityKeys = bobAccount.identityKeys();
        } catch (Exception e) {
            assertTrue("identityKeys failed " + e.getMessage(), false);
        }

        bobIdentityKey = TestHelper.getIdentityKey(bobIdentityKeys);
        assertTrue(null!=bobIdentityKey);

        // get bob one time keys
        try {
            bobAccount.generateOneTimeKeys(ONE_TIME_KEYS_NUMBER);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        Map<String, Map<String, String>> bobOneTimeKeys = null;

        try {
            bobOneTimeKeys = bobAccount.oneTimeKeys();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        bobOneTimeKey = TestHelper.getOneTimeKey(bobOneTimeKeys,1);
        assertNotNull(bobOneTimeKey);

        // CREATE ALICE SESSION
        OlmSession aliceSession = null;
        try {
            aliceSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }
        assertTrue(0!=aliceSession.getOlmSessionId());

        // CREATE ALICE OUTBOUND SESSION and encrypt message to bob
        try {
            aliceSession.initOutboundSession(aliceAccount, bobIdentityKey, bobOneTimeKey);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        String helloClearMsg = "Hello I'm Alice!";

        OlmMessage encryptedAliceToBobMsg1 = null;

        try {
            encryptedAliceToBobMsg1 = aliceSession.encryptMessage(helloClearMsg);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(encryptedAliceToBobMsg1);
        assertNotNull(encryptedAliceToBobMsg1.mCipherText);

        // CREATE BOB INBOUND SESSION and decrypt message from alice
        OlmSession bobSession = null;
        try {
            bobSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }

        assertTrue(0!=bobSession.getOlmSessionId());

        try {
            bobSession.initInboundSession(bobAccount, encryptedAliceToBobMsg1.mCipherText);
        } catch (Exception e) {
            assertTrue("initInboundSessionWithAccount failed " + e.getMessage(), false);
        }

        // DECRYPT MESSAGE FROM ALICE
        String decryptedMsg01 = null;
        try {
            decryptedMsg01 = bobSession.decryptMessage(encryptedAliceToBobMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(decryptedMsg01);

        // MESSAGE COMPARISON: decrypted vs encrypted
        assertTrue(helloClearMsg.equals(decryptedMsg01));

        // BACK/FORTH MESSAGE COMPARISON
        String clearMsg1 = "Hello I'm Bob!";
        String clearMsg2 = "Isn't life grand?";
        String clearMsg3 = "Let's go to the opera.";

        // bob encrypts messages
        OlmMessage encryptedMsg1 = null;
        try {
            encryptedMsg1 = bobSession.encryptMessage(clearMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg1);

        OlmMessage encryptedMsg2 = null;
        try {
            encryptedMsg2 = bobSession.encryptMessage(clearMsg2);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg2);


        OlmMessage encryptedMsg3 = null;
        try {
            encryptedMsg3 = bobSession.encryptMessage(clearMsg3);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg3);

        // alice decrypts bob's messages
        String decryptedMsg1 = null;
        try {
            decryptedMsg1 = aliceSession.decryptMessage(encryptedMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(decryptedMsg1);
        String decryptedMsg2 = null;
        try {
            decryptedMsg2 = aliceSession.decryptMessage(encryptedMsg2);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(decryptedMsg2);
        String decryptedMsg3 = null;
        try {
            decryptedMsg3 = aliceSession.decryptMessage(encryptedMsg3);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(decryptedMsg3);

        // comparison tests
        assertTrue(clearMsg1.equals(decryptedMsg1));
        assertTrue(clearMsg2.equals(decryptedMsg2));
        assertTrue(clearMsg3.equals(decryptedMsg3));

        // and one more from alice to bob
        clearMsg1 = "another message from Alice to Bob!!";
        encryptedMsg1 = null;

        try {
            encryptedMsg1 = aliceSession.encryptMessage(clearMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg1);

        decryptedMsg1 = null;
        try {
            decryptedMsg1 = bobSession.decryptMessage(encryptedMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(decryptedMsg1);
        assertTrue(clearMsg1.equals(decryptedMsg1));

        // comparison test
        assertTrue(clearMsg1.equals(decryptedMsg1));

        // clean objects..
        try {
            bobAccount.removeOneTimeKeys(bobSession);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        bobAccount.releaseAccount();
        aliceAccount.releaseAccount();
        assertTrue(bobAccount.isReleased());
        assertTrue(aliceAccount.isReleased());

        bobSession.releaseSession();
        aliceSession.releaseSession();
        assertTrue(bobSession.isReleased());
        assertTrue(aliceSession.isReleased());
    }


    @Test
    public void test03AliceBobSessionId() {
        // creates alice & bob accounts
        OlmAccount aliceAccount = null;
        OlmAccount bobAccount = null;
        try {
            aliceAccount = new OlmAccount();
            bobAccount = new OlmAccount();
        } catch (OlmException e) {
            assertTrue(e.getMessage(),false);
        }

        // test accounts creation
        assertTrue(0!=bobAccount.getOlmAccountId());
        assertTrue(0!=aliceAccount.getOlmAccountId());

        // CREATE ALICE SESSION

        OlmSession aliceSession = null;
        try {
            aliceSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }
        assertTrue(0!=aliceSession.getOlmSessionId());

        // CREATE ALICE SESSION
        OlmSession bobSession = null;
        try {
            bobSession = new OlmSession();
        } catch (OlmException e) {
            e.printStackTrace();
            assertTrue(e.getMessage(), false);
        }
        assertTrue(0!=bobSession.getOlmSessionId());

        String aliceSessionId = null;
        try {
            aliceSessionId = aliceSession.sessionIdentifier();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(aliceSessionId);

        String bobSessionId = null;
        try {
            bobSessionId = bobSession.sessionIdentifier();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(bobSessionId);

        // must be the same for both ends of the conversation
        assertTrue(aliceSessionId.equals(bobSessionId));

        aliceAccount.releaseAccount();
        bobAccount.releaseAccount();
        assertTrue(aliceAccount.isReleased());
        assertTrue(bobAccount.isReleased());

        bobSession.releaseSession();
        aliceSession.releaseSession();
        assertTrue(bobSession.isReleased());
        assertTrue(aliceSession.isReleased());
    }

    @Test
    public void test04MatchInboundSession() {
        OlmAccount aliceAccount=null, bobAccount=null;
        OlmSession aliceSession = null, bobSession = null;

        // ACCOUNTS CREATION
        try {
            aliceAccount = new OlmAccount();
            bobAccount = new OlmAccount();
        } catch (OlmException e) {
            assertTrue(e.getMessage(), false);
        }

        // CREATE ALICE SESSION
        try {
            aliceSession = new OlmSession();
            bobSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg=" + e.getMessage(), false);
        }

        // get bob/luke identity key
        Map<String, String> bobIdentityKeys = null;

        try {
            bobIdentityKeys = bobAccount.identityKeys();
        } catch (Exception e) {
            assertTrue("identityKeys failed " + e.getMessage(), false);
        }

        Map<String, String> aliceIdentityKeys = null;

        try {
            aliceIdentityKeys = aliceAccount.identityKeys();
        } catch (Exception e) {
            assertTrue("identityKeys failed " + e.getMessage(), false);
        }

        String bobIdentityKey = TestHelper.getIdentityKey(bobIdentityKeys);
        String aliceIdentityKey = TestHelper.getIdentityKey(aliceIdentityKeys);

        // get bob/luke one time keys
        try {
            bobAccount.generateOneTimeKeys(ONE_TIME_KEYS_NUMBER);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        try {
            aliceAccount.generateOneTimeKeys(ONE_TIME_KEYS_NUMBER);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        Map<String, Map<String, String>> bobOneTimeKeys = null;

        try {
            bobOneTimeKeys = bobAccount.oneTimeKeys();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        String bobOneTimeKey1 = TestHelper.getOneTimeKey(bobOneTimeKeys, 1);

        // create alice inbound session for bob
        try {
            aliceSession.initOutboundSession(aliceAccount, bobIdentityKey, bobOneTimeKey1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        String aliceClearMsg = "hello helooo to bob!";
        OlmMessage encryptedAliceToBobMsg1 = null;

        try {
            encryptedAliceToBobMsg1 = aliceSession.encryptMessage(aliceClearMsg);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertFalse(bobSession.matchesInboundSession(encryptedAliceToBobMsg1.mCipherText));

        // init bob session with alice PRE KEY
        try {
            bobSession.initInboundSession(bobAccount, encryptedAliceToBobMsg1.mCipherText);
        } catch (Exception e) {
            assertTrue("initInboundSessionWithAccount failed " + e.getMessage(), false);
        }

        // test matchesInboundSession() and matchesInboundSessionFrom()
        assertTrue(bobSession.matchesInboundSession(encryptedAliceToBobMsg1.mCipherText));
        assertTrue(bobSession.matchesInboundSessionFrom(aliceIdentityKey, encryptedAliceToBobMsg1.mCipherText));
        // following requires olm native lib new version with https://github.com/matrix-org/olm-backup/commit/7e9f3bebb8390f975a76c0188ce4cb460fe6692e
        //assertTrue(false==bobSession.matchesInboundSessionFrom(bobIdentityKey, encryptedAliceToBobMsg1.mCipherText));

        // release objects
        try {
            bobAccount.removeOneTimeKeys(bobSession);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        aliceAccount.releaseAccount();
        bobAccount.releaseAccount();
        assertTrue(aliceAccount.isReleased());
        assertTrue(bobAccount.isReleased());

        aliceSession.releaseSession();
        bobSession.releaseSession();
        assertTrue(aliceSession.isReleased());
        assertTrue(bobSession.isReleased());
    }

    // ********************************************************
    // ************* SERIALIZATION TEST ***********************
    // ********************************************************
    /**
     * Same as {@link #test02AliceToBobBackAndForth()}, but alice's session
     * is serialized and de-serialized before performing the final
     * comparison (encrypt vs )
     */
    @Test
    public void test05SessionSerialization() {
        final int ONE_TIME_KEYS_NUMBER = 1;
        String bobIdentityKey;
        String bobOneTimeKey;
        OlmAccount aliceAccount = null;
        OlmAccount bobAccount = null;
        OlmSession aliceSessionDeserial = null;

        // creates alice & bob accounts
        try {
            aliceAccount = new OlmAccount();
            bobAccount = new OlmAccount();
        } catch (OlmException e) {
            assertTrue(e.getMessage(),false);
        }

        // test accounts creation
        assertTrue(0!=bobAccount.getOlmAccountId());
        assertTrue(0!=aliceAccount.getOlmAccountId());

        // get bob identity key
        Map<String, String> bobIdentityKeys = null;

        try {
            bobIdentityKeys = bobAccount.identityKeys();
        } catch (Exception e) {
            assertTrue("identityKeys failed " + e.getMessage(), false);
        }

        bobIdentityKey = TestHelper.getIdentityKey(bobIdentityKeys);
        assertTrue(null!=bobIdentityKey);

        // get bob one time keys
        try {
            bobAccount.generateOneTimeKeys(ONE_TIME_KEYS_NUMBER);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        Map<String, Map<String, String>> bobOneTimeKeys = null;

        try {
            bobOneTimeKeys = bobAccount.oneTimeKeys();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        bobOneTimeKey = TestHelper.getOneTimeKey(bobOneTimeKeys,1);
        assertNotNull(bobOneTimeKey);

        // CREATE ALICE SESSION
        OlmSession aliceSession = null;
        try {
            aliceSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }
        assertTrue(0!=aliceSession.getOlmSessionId());

        // CREATE ALICE OUTBOUND SESSION and encrypt message to bob
        try {
            aliceSession.initOutboundSession(aliceAccount, bobIdentityKey, bobOneTimeKey);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        String helloClearMsg = "Hello I'm Alice!";

        OlmMessage encryptedAliceToBobMsg1 = null;
        try {
            encryptedAliceToBobMsg1 = aliceSession.encryptMessage(helloClearMsg);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedAliceToBobMsg1);
        assertNotNull(encryptedAliceToBobMsg1.mCipherText);

        // CREATE BOB INBOUND SESSION and decrypt message from alice
        OlmSession bobSession = null;
        try {
            bobSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }
        assertTrue(0!=bobSession.getOlmSessionId());

        // init bob session with alice PRE KEY
        try {
            bobSession.initInboundSession(bobAccount, encryptedAliceToBobMsg1.mCipherText);
        } catch (Exception e) {
            assertTrue("initInboundSessionWithAccount failed " + e.getMessage(), false);
        }

        // DECRYPT MESSAGE FROM ALICE
        String decryptedMsg01 = null;

        try {
            decryptedMsg01 = bobSession.decryptMessage(encryptedAliceToBobMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(decryptedMsg01);

        // MESSAGE COMPARISON: decrypted vs encrypted
        assertTrue(helloClearMsg.equals(decryptedMsg01));

        // BACK/FORTH MESSAGE COMPARISON
        String clearMsg1 = "Hello I'm Bob!";
        String clearMsg2 = "Isn't life grand?";
        String clearMsg3 = "Let's go to the opera.";

        // bob encrypts messages
        OlmMessage encryptedMsg1 = null;
        try {
            encryptedMsg1 = bobSession.encryptMessage(clearMsg1);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg1);

        OlmMessage encryptedMsg2 = null;
        try {
            encryptedMsg2 = bobSession.encryptMessage(clearMsg2);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg2);

        OlmMessage encryptedMsg3 = null;
        try {
            encryptedMsg3 = bobSession.encryptMessage(clearMsg3);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsg3);

        // serialize alice session
        Context context = getInstrumentation().getContext();
        try {
            FileOutputStream fileOutput = context.openFileOutput(FILE_NAME_SERIAL_SESSION, Context.MODE_PRIVATE);
            ObjectOutputStream objectOutput = new ObjectOutputStream(fileOutput);
            objectOutput.writeObject(aliceSession);
            objectOutput.flush();
            objectOutput.close();

            // deserialize session
            FileInputStream fileInput = context.openFileInput(FILE_NAME_SERIAL_SESSION);
            ObjectInputStream objectInput = new ObjectInputStream(fileInput);
            aliceSessionDeserial = (OlmSession) objectInput.readObject();
            objectInput.close();

            // test deserialize return value
            assertNotNull(aliceSessionDeserial);

            // de-serialized alice session decrypts bob's messages
            String decryptedMsg1 = aliceSessionDeserial.decryptMessage(encryptedMsg1);
            assertNotNull(decryptedMsg1);
            String decryptedMsg2 = aliceSessionDeserial.decryptMessage(encryptedMsg2);
            assertNotNull(decryptedMsg2);
            String decryptedMsg3 = aliceSessionDeserial.decryptMessage(encryptedMsg3);
            assertNotNull(decryptedMsg3);

            // comparison tests
            assertTrue(clearMsg1.equals(decryptedMsg1));
            assertTrue(clearMsg2.equals(decryptedMsg2));
            assertTrue(clearMsg3.equals(decryptedMsg3));

            // clean objects..
            try {
                bobAccount.removeOneTimeKeys(bobSession);
            } catch (Exception e) {
                assertTrue(e.getMessage(), false);
            }

            bobAccount.releaseAccount();
            aliceAccount.releaseAccount();
            assertTrue(bobAccount.isReleased());
            assertTrue(aliceAccount.isReleased());

            bobSession.releaseSession();
            aliceSession.releaseSession();
            aliceSessionDeserial.releaseSession();
            assertTrue(bobSession.isReleased());
            assertTrue(aliceSession.isReleased());
            assertTrue(aliceSessionDeserial.isReleased());
        }
        catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "## test03SessionSerialization(): Exception FileNotFoundException Msg=="+e.getMessage());
            assertTrue(e.getMessage(), false);
        }
        catch (ClassNotFoundException e) {
            Log.e(LOG_TAG, "## test03SessionSerialization(): Exception ClassNotFoundException Msg==" + e.getMessage());
            assertTrue(e.getMessage(), false);
        }
        catch (IOException e) {
            Log.e(LOG_TAG, "## test03SessionSerialization(): Exception IOException Msg==" + e.getMessage());
            assertTrue(e.getMessage(), false);
        }
        /*catch (OlmException e) {
            Log.e(LOG_TAG, "## test03SessionSerialization(): Exception OlmException Msg==" + e.getMessage());
        }*/
        catch (Exception e) {
            Log.e(LOG_TAG, "## test03SessionSerialization(): Exception Msg==" + e.getMessage());
            assertTrue(e.getMessage(), false);
        }
    }


    // ****************************************************
    // *************** SANITY CHECK TESTS *****************
    // ****************************************************

    @Test
    public void test06SanityCheckErrors() {
        final int ONE_TIME_KEYS_NUMBER = 5;
        OlmAccount bobAccount = null;
        OlmAccount aliceAccount = null;

        // ALICE & BOB ACCOUNTS CREATION
        try {
            aliceAccount = new OlmAccount();
            bobAccount = new OlmAccount();
        } catch (OlmException e) {
            assertTrue(e.getMessage(), false);
        }

        // get bob identity key
        Map<String, String> bobIdentityKeys = null;

        try {
            bobIdentityKeys = bobAccount.identityKeys();
        } catch (Exception e) {
            assertTrue("identityKeys failed " + e.getMessage(), false);
        }

        String bobIdentityKey = TestHelper.getIdentityKey(bobIdentityKeys);
        assertTrue(null != bobIdentityKey);

        // get bob one time keys
        try {
            bobAccount.generateOneTimeKeys(ONE_TIME_KEYS_NUMBER);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        Map<String, Map<String, String>> bobOneTimeKeys = null;

        try {
            bobOneTimeKeys = bobAccount.oneTimeKeys();
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertNotNull(bobOneTimeKeys);
        String bobOneTimeKey = TestHelper.getOneTimeKey(bobOneTimeKeys,1);
        assertNotNull(bobOneTimeKey);

        // CREATE ALICE SESSION
        OlmSession aliceSession = null;
        try {
            aliceSession = new OlmSession();
        } catch (OlmException e) {
            assertTrue("Exception Msg=" + e.getMessage(), false);
        }

        // SANITY CHECK TESTS FOR: initOutboundSessionWithAccount()
        String errorMessage = null;
        try {
            aliceSession.initOutboundSession(null, bobIdentityKey, bobOneTimeKey);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        assertTrue(null != errorMessage);

        errorMessage = null;
        try {
            aliceSession.initOutboundSession(aliceAccount, null, bobOneTimeKey);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        assertTrue(null != errorMessage);

        errorMessage = null;
        try {
            aliceSession.initOutboundSession(aliceAccount, bobIdentityKey, null);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        assertTrue(null != errorMessage);

        errorMessage = null;
        try {
            aliceSession.initOutboundSession(null, null, null);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        assertTrue(null != errorMessage);

        // init properly
        errorMessage = null;
        try {
            aliceSession.initOutboundSession(aliceAccount, bobIdentityKey, bobOneTimeKey);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }
        assertTrue(null == errorMessage);

        // SANITY CHECK TESTS FOR: encryptMessage()
        OlmMessage message = null;
        try {
            message = aliceSession.encryptMessage(null);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertTrue(null==message);

        // encrypt properly
        OlmMessage encryptedMsgToBob = null;
        try {
            encryptedMsgToBob = aliceSession.encryptMessage("A message for bob");
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }
        assertNotNull(encryptedMsgToBob);

        // SANITY CHECK TESTS FOR: initInboundSessionWithAccount()
        OlmSession bobSession = null;
        try {
            bobSession = new OlmSession();
            errorMessage = null;
            try {
                bobSession.initInboundSession(null, encryptedMsgToBob.mCipherText);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }

            assertTrue(!TextUtils.isEmpty(errorMessage));

            errorMessage = null;
            try {
                bobSession.initInboundSession(bobAccount, null);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }

            assertTrue(!TextUtils.isEmpty(errorMessage));

            errorMessage = null;
            try {
                bobSession.initInboundSession(bobAccount, INVALID_PRE_KEY);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }

            assertTrue(!TextUtils.isEmpty(errorMessage));

            // init properly
            errorMessage = null;
            try {
                bobSession.initInboundSession(bobAccount, encryptedMsgToBob.mCipherText);
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }

            assertTrue(TextUtils.isEmpty(errorMessage));
        } catch (OlmException e) {
            assertTrue("Exception Msg="+e.getMessage(), false);
        }

        // SANITY CHECK TESTS FOR: decryptMessage()
        String decryptedMsg = null;
        try {
            decryptedMsg = aliceSession.decryptMessage(null);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        assertTrue(null==decryptedMsg);

        // SANITY CHECK TESTS FOR: matchesInboundSession()
        assertTrue(!aliceSession.matchesInboundSession(null));

        // SANITY CHECK TESTS FOR: matchesInboundSessionFrom()
        assertTrue(!aliceSession.matchesInboundSessionFrom(null,null));

        // release objects
        try {
            bobAccount.removeOneTimeKeys(bobSession);
        } catch (Exception e) {
            assertTrue(e.getMessage(), false);
        }

        aliceAccount.releaseAccount();
        bobAccount.releaseAccount();
        assertTrue(aliceAccount.isReleased());
        assertTrue(bobAccount.isReleased());

        aliceSession.releaseSession();
        bobSession.releaseSession();
        assertTrue(aliceSession.isReleased());
        assertTrue(bobSession.isReleased());
    }

}
