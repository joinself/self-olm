/* Copyright 2016 OpenMarket Ltd
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
#include "self_olm/inbound_group_session.h"
#include "self_olm/outbound_group_session.h"
#include "unittest.hh"

#include <vector>

int main() {

{
    TestCase test_case("Pickle outbound group session");

    size_t size = olm_outbound_group_session_size();
    std::vector<uint8_t> memory(size);
    OlmOutboundGroupSession *session = olm_outbound_group_session(memory.data());

    size_t pickle_length = olm_pickle_outbound_group_session_length(session);
    std::vector<uint8_t> pickle1(pickle_length);
    size_t res = olm_pickle_outbound_group_session(
        session, "secret_key", 10, pickle1.data(), pickle_length
    );
    assert_equals(pickle_length, res);

    std::vector<uint8_t> pickle2(pickle1);

    std::vector<uint8_t> buffer2(size);
    OlmOutboundGroupSession *session2 = olm_outbound_group_session(buffer2.data());
    res = olm_unpickle_outbound_group_session(
        session2, "secret_key", 10, pickle2.data(), pickle_length
    );
    assert_not_equals((size_t)-1, res);
    assert_equals(pickle_length,
                  olm_pickle_outbound_group_session_length(session2));
    res = olm_pickle_outbound_group_session(
        session2, "secret_key", 10, pickle2.data(), pickle_length
    );
    assert_equals(pickle_length, res);

    assert_equals(pickle1.data(), pickle2.data(), pickle_length);
}


{
    TestCase test_case("Pickle inbound group session");

    size_t size = olm_inbound_group_session_size();
    std::vector<uint8_t> memory(size);
    OlmInboundGroupSession *session = olm_inbound_group_session(memory.data());

    size_t pickle_length = olm_pickle_inbound_group_session_length(session);
    std::vector<uint8_t> pickle1(pickle_length);
    size_t res = olm_pickle_inbound_group_session(
        session, "secret_key", 10, pickle1.data(), pickle_length
    );
    assert_equals(pickle_length, res);

    std::vector<uint8_t> pickle2(pickle1);

    std::vector<uint8_t> buffer2(size);
    OlmInboundGroupSession *session2 = olm_inbound_group_session(buffer2.data());
    res = olm_unpickle_inbound_group_session(
        session2, "secret_key", 10, pickle2.data(), pickle_length
    );
    assert_not_equals((size_t)-1, res);
    assert_equals(pickle_length,
                  olm_pickle_inbound_group_session_length(session2));
    res = olm_pickle_inbound_group_session(
        session2, "secret_key", 10, pickle2.data(), pickle_length
    );

    assert_equals(pickle1.data(), pickle2.data(), pickle_length);
}

{
    TestCase test_case("Group message send/receive");

    uint8_t random_bytes[] =
        "0123456789ABDEF0123456789ABCDEF"
        "0123456789ABDEF0123456789ABCDEF"
        "0123456789ABDEF0123456789ABCDEF"
        "0123456789ABDEF0123456789ABCDEF"
        "0123456789ABDEF0123456789ABCDEF"
        "0123456789ABDEF0123456789ABCDEF";


    /* build the outbound session */
    size_t size = olm_outbound_group_session_size();
    std::vector<uint8_t> memory(size);
    OlmOutboundGroupSession *session = olm_outbound_group_session(memory.data());

    assert_equals((size_t)160,
                  olm_init_outbound_group_session_random_length(session));

    size_t res = olm_init_outbound_group_session(
        session, random_bytes, sizeof(random_bytes));
    assert_equals((size_t)0, res);

    assert_equals(0U, olm_outbound_group_session_message_index(session));
    size_t session_key_len = olm_outbound_group_session_key_length(session);
    std::vector<uint8_t> session_key(session_key_len);
    olm_outbound_group_session_key(session, session_key.data(), session_key_len);

    /* encode the message */
    uint8_t plaintext[] = "Message";
    size_t plaintext_length = sizeof(plaintext) - 1;

    size_t msglen = olm_group_encrypt_message_length(
        session, plaintext_length);

    std::vector<uint8_t> msg(msglen);
    res = olm_group_encrypt(session, plaintext, plaintext_length,
                            msg.data(), msglen);
    assert_equals(msglen, res);
    assert_equals(1U, olm_outbound_group_session_message_index(session));

    /* build the inbound session */
    size = olm_inbound_group_session_size();
    std::vector<uint8_t> inbound_session_memory(size);
    OlmInboundGroupSession *inbound_session =
        olm_inbound_group_session(inbound_session_memory.data());

    assert_equals(0, olm_inbound_group_session_is_verified(inbound_session));

    res = olm_init_inbound_group_session(
        inbound_session, session_key.data(), session_key_len);
    assert_equals((size_t)0, res);
    assert_equals(1, olm_inbound_group_session_is_verified(inbound_session));

    /* Check the session ids */

    size_t out_session_id_len = olm_outbound_group_session_id_length(session);
    std::vector<uint8_t> out_session_id(out_session_id_len);
    assert_equals(out_session_id_len, olm_outbound_group_session_id(
        session, out_session_id.data(), out_session_id_len
    ));

    size_t in_session_id_len = olm_inbound_group_session_id_length(
        inbound_session
    );
    std::vector<uint8_t> in_session_id(in_session_id_len);
    assert_equals(in_session_id_len, olm_inbound_group_session_id(
        inbound_session, in_session_id.data(), in_session_id_len
    ));

    assert_equals(in_session_id_len, out_session_id_len);
    assert_equals(out_session_id.data(), in_session_id.data(), in_session_id_len);

    /* decode the message */

    /* olm_group_decrypt_max_plaintext_length destroys the input so we have to
       copy it. */
    std::vector<uint8_t> msgcopy(msg);
    size = olm_group_decrypt_max_plaintext_length(inbound_session, msgcopy.data(), msglen);
    std::vector<uint8_t> plaintext_buf(size);
    uint32_t message_index;
    res = olm_group_decrypt(inbound_session, msg.data(), msglen,
                            plaintext_buf.data(), size, &message_index);
    assert_equals(plaintext_length, res);
    assert_equals(plaintext, plaintext_buf.data(), res);
    assert_equals(message_index, uint32_t(0));
}

{
    TestCase test_case("Inbound group session export/import");

    uint8_t session_key[] =
        "AgAAAAAwMTIzNDU2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMzQ1Njc4OUFCREVGM"
        "DEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkRFRjAxMjM0NTY3ODlBQkNERUYwMTIzND"
        "U2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMw0bdg1BDq4Px/slBow06q8n/B9WBfw"
        "WYyNOB8DlUmXGGwrFmaSb9bR/eY8xgERrxmP07hFmD9uqA2p8PMHdnV5ysmgufE6oLZ5+"
        "8/mWQOW3VVTnDIlnwd8oHUYRuk8TCQ";

    const uint8_t message[] =
        "AwgAEhAcbh6UpbByoyZxufQ+h2B+8XHMjhR69G8F4+qjMaFlnIXusJZX3r8LnRORG9T3D"
        "XFdbVuvIWrLyRfm4i8QRbe8VPwGRFG57B1CtmxanuP8bHtnnYqlwPsD";
    const std::size_t msglen = sizeof(message)-1;

    /* init first inbound group session, and decrypt */
    std::size_t size = olm_inbound_group_session_size();
    std::vector<uint8_t> session_memory1(size);
    OlmInboundGroupSession *session1 =
        olm_inbound_group_session(session_memory1.data());
    assert_equals(0, olm_inbound_group_session_is_verified(session1));

    std::size_t res = olm_init_inbound_group_session(
        session1, session_key, sizeof(session_key)-1
    );
    assert_equals((size_t)0, res);
    assert_equals(1, olm_inbound_group_session_is_verified(session1));

    /* olm_group_decrypt_max_plaintext_length destroys the input so we have to
       copy it. */
    std::vector<uint8_t> msgcopy(msglen);
    memcpy(msgcopy.data(), message, msglen);
    size = olm_group_decrypt_max_plaintext_length(session1, msgcopy.data(), msglen);
    std::vector<uint8_t> plaintext_buf(size);
    uint32_t message_index;
    memcpy(msgcopy.data(), message, msglen);
    res = olm_group_decrypt(
        session1, msgcopy.data(), msglen, plaintext_buf.data(), size, &message_index
    );
    assert_equals((std::size_t)7, res);
    assert_equals((const uint8_t *)"Message", plaintext_buf.data(), res);
    assert_equals(uint32_t(0), message_index);

    /* export the keys */
    size = olm_export_inbound_group_session_length(session1);
    std::vector<uint8_t> export_memory(size);
    res = olm_export_inbound_group_session(
        session1, export_memory.data(), size, 0
    );
    assert_equals(size, res);

    /* free the old session to check there is no shared data */
    olm_clear_inbound_group_session(session1);

    /* import the keys into another inbound group session */
    size = olm_inbound_group_session_size();
    std::vector<uint8_t> session_memory2(size);
    OlmInboundGroupSession *session2 =
        olm_inbound_group_session(session_memory2.data());
    res = olm_import_inbound_group_session(
        session2, export_memory.data(), export_memory.size()
    );
    assert_equals((size_t)0, res);
    assert_equals(0, olm_inbound_group_session_is_verified(session2));

    /* decrypt the message with the new session */
    memcpy(msgcopy.data(), message, msglen);
    size = olm_group_decrypt_max_plaintext_length(session2, msgcopy.data(), msglen);
    std::vector<uint8_t> plaintext_buf2(size);
    memcpy(msgcopy.data(), message, msglen);
    res = olm_group_decrypt(
        session2, msgcopy.data(), msglen, plaintext_buf2.data(), size, &message_index
    );
    assert_equals((std::size_t)7, res);
    assert_equals((const uint8_t *)"Message", plaintext_buf2.data(), res);
    assert_equals(uint32_t(0), message_index);
    assert_equals(1, olm_inbound_group_session_is_verified(session2));
}

{
    TestCase test_case("Invalid signature group message");

    uint8_t plaintext[] = "Message";
    size_t plaintext_length = sizeof(plaintext) - 1;

    uint8_t session_key[] =
        "AgAAAAAwMTIzNDU2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMzQ1Njc4OUFCREVGM"
        "DEyMzQ1Njc4OUFCQ0RFRjAxMjM0NTY3ODlBQkRFRjAxMjM0NTY3ODlBQkNERUYwMTIzND"
        "U2Nzg5QUJERUYwMTIzNDU2Nzg5QUJDREVGMDEyMztqJ7zOtqQtYqOo0CpvDXNlMhV3HeJ"
        "DpjrASKGLWdop4lx1cSN3Xv1TgfLPW8rhGiW+hHiMxd36nRuxscNv9k4oJA/KP+o0mi1w"
        "v44StrEJ1wwx9WZHBUIWkQbaBSuBDw";

    uint8_t message[] =
        "AwgAEhAcbh6UpbByoyZxufQ+h2B+8XHMjhR69G8nP4pNZGl/3QMgrzCZPmP+F2aPLyKPz"
        "xRPBMUkeXRJ6Iqm5NeOdx2eERgTW7P20CM+lL3Xpk+ZUOOPvsSQNaAL";
    size_t msglen = sizeof(message)-1;

    /* build the inbound session */
    size_t size = olm_inbound_group_session_size();
    std::vector<uint8_t> inbound_session_memory(size);
    OlmInboundGroupSession *inbound_session =
        olm_inbound_group_session(inbound_session_memory.data());

    size_t res = olm_init_inbound_group_session(
        inbound_session, session_key, sizeof(session_key)-1
    );
    assert_equals((size_t)0, res);

    /* decode the message */

    /* olm_group_decrypt_max_plaintext_length destroys the input so we have to
       copy it. */
    std::vector<uint8_t> msgcopy(msglen);
    memcpy(msgcopy.data(), message, msglen);
    size = olm_group_decrypt_max_plaintext_length(
        inbound_session, msgcopy.data(), msglen
    );

    memcpy(msgcopy.data(), message, msglen);
    std::vector<uint8_t> plaintext_buf(size);
    uint32_t message_index;
    res = olm_group_decrypt(
        inbound_session, msgcopy.data(), msglen, plaintext_buf.data(), size, &message_index
    );
    assert_equals(message_index, uint32_t(0));
    assert_equals(plaintext_length, res);
    assert_equals(plaintext, plaintext_buf.data(), res);

    /* now twiddle the signature */
    message[msglen-1] = 'E';
    memcpy(msgcopy.data(), message, msglen);
    assert_equals(
        size,
        olm_group_decrypt_max_plaintext_length(
            inbound_session, msgcopy.data(), msglen
        )
    );

    memcpy(msgcopy.data(), message, msglen);
    res = olm_group_decrypt(
        inbound_session, msgcopy.data(), msglen,
        plaintext_buf.data(), size, &message_index
    );
    assert_equals((size_t)-1, res);
    assert_equals(
        std::string("BAD_SIGNATURE"),
        std::string(olm_inbound_group_session_last_error(inbound_session))
    );
}


}
