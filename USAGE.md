# OLM USAGE

## Accounts

### New Account

The first time you use an account, you will need to create it from the ed25519
seed used for self accounts:

```go
func main() {
    // allocate some memory for the olm account as a byte array.
    // 'olm_account_size' will return the size needed for the array
    buf := make([]byte, C.olm_account_size())

    // pass in the allocated memory to `olm_account`. this will return a pointer
    // to the olm account. You should pass the address of the first byte of the
    // array you allocated above.
    acc := C.olm_account(unsafe.Pointer(&buf[0]))

    // here we use the custom function 'olm_create_account_derrived_keys' that
    // accepts the ed25519 seed we use for the self account. It will use this
    // and derrive a curve25519 key for it.
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the address of the first byte in the raw ed25519 seed from a self
    //    accounts identity key
    // 3. the size of the key
    C.olm_create_account_derrived_keys(
		acc,
		unsafe.Pointer(&seed[0]),
		C.size_t(len(seed)),
	)
}
```

### Existing Account

If you have previously created an account and have stored an encrypted pickle of
it and would like to reload it, you can do this as follows:

```go
func main() {
    // allocate some memory for the olm account as a byte array.
    // 'olm_account_size' will return the size needed for the array
    buf := make([]byte, C.olm_account_size())

    // pass in the allocated memory to `olm_account`. this will return a pointer
    // to the olm account. You should pass the address of the first byte of the
    // array you allocated above.
    acc := C.olm_account(unsafe.Pointer(&buf[0]))

    // cast the encryption key string used to encrypt the account to a byte array
    kbuf := []byte(key)

    // cast the pickled/encoded account from a string to a byte array
	pbuf := []byte(pickle)

    // unpickle the account using 'olm_account_unpickle'. This will deserialize
    // and decrypt the account and load its contents into the account pointer.
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the address of the first byte in the encryption key byte array
    // 3. the size of the encryption key byte array
    // 4. the address of the first byte in the account pickle byte array
	C.olm_unpickle_account(
		acc,
		unsafe.Pointer(&kbuf[0]),
		C.size_t(len(kbuf)),
		unsafe.Pointer(&pbuf[0]),
		C.size_t(len(pbuf)),
	)
}
```

### Storing Account

If you have an account you wish to store, you should create an encrypted pickle
of it and persist it somewhere for later user. You should create and store a
pickle of the account any time you create a new account from a seed or generate
new one time keys for the account.

```go
func main() {
    // account generated via one of the above steps is the 'acc' variable
    ...

    // this is the user provided key thats used to encrypt the account
    kbuf := []byte(key)

    // allocate some memory for the olm account pickle as a byte array.
    // 'olm_pickle_account_length' will return the size needed for the array
    pbuf := make([]byte, C.olm_pickle_account_length(acc))

    // create a pickle of the account using 'olm_pickle_account'. this writes
    // a base64 encoded string to the pickle buffer allocated above. this should
    // be persisted and used to reload the account in the 'Existing Account'
    // step above.
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the address of the first byte in the encryption key byte array
    // 3. the size of the encryption key byte array
    // 4. the address of the first byte in the allocated pickle byte array
    // 5. the size of the pickle byte array
    C.olm_pickle_account(
        acc,
        unsafe.Pointer(&kbuf[0]),
        C.size_t(len(kbuf)),
        unsafe.Pointer(&pbuf[0]),
        C.size_t(len(pbuf)),
    )
}
```

### Creating One Time Keys

In order for other identities to communicate with your identity, you must
publish some prekeys. These keys are single usage, where one key will be handed
out to an identity that requests them.

It is the job of the client to track when all keys have been exhausted and
publish new keys to the server.

```go
func main() {
    // account generated via one of the above steps is the 'acc' variable
    ...

    // use 'olm_account_generate_one_time_keys_random_length' to get the size
    // of the random data needed to generate the keys.
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the number of keys to be generated. 100 is a sensible value for most
    //    clients, however, up to 100000 are supported for extreme cases where
    //    there are identities where their keys.
    rlen := C.olm_account_generate_one_time_keys_random_length(
		acc,
		C.size_t(100),
	)

    // allocate a byte array to hold the random data
	rbuf := make([]byte, rlen)

    // read some random data into the buffer
	_, err := rand.Read(rbuf)
	if err != nil {
		return err
	}

    // generate some one time keys using 'olm_account_generate_one_time_keys'
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the number of keys to be generated. this should be the same as the
    //    call to 'olm_account_generate_one_time_keys_random_length'
    // 3. the address of the first byte in the allocated random byte array
    // 4. the size of the random byte array
	C.olm_account_generate_one_time_keys(
		acc,
		C.size_t(count),
		unsafe.Pointer(&rbuf[0]),
		rlen,
	)

    // check the account for any errors when generating the one time keys
    // 'olm_account_last_error' returns a c string with an error message
    // detailing any error. it will return 'SUCCESS' if the operation completed
    lastError := C.olm_account_last_error(acc)
	if C.CString(lastError) == "SUCCESS" {
        // succeeded
    } else {
        // errored
    }
}
```

### Retrieving One Time Keys

Prekeys are stored as part of the account. Once you have created some prekeys,
you should follow the steps bellow to retrieve them from the account.

Olm will return a structure as follows:

```json
{
    "curve25519": {
        "AAAABQ": "5XabHkWEdc+5zB7D1EZfffnj2s6Lv17OXj+ml2TFlTI",
        "AAAABA": "4ngvDuLW7eH/NiTPY1vSS8Dl9ICqz0RpeSNWz91rqV8",
        "AAAAAw": "rCCgpJbAJKnQ6AvqjH4g/pff+YwqbY4mCmWQ/5ez2Ac",
        "AAAAAg": "/JVZwRJWlZQY2sIqLpfRdyDKW15/NZGLT7AYde2IrUs",
        "AAAAAQ": "nowJstVupM7pOX9xm9qedq/UncY/bn6AcUnZf7q5qVo"
    }
}
```

Publishing prekeys to the server, these keys will need to be coerced into the
following structure:

```json
[
    {
        "id": "AAAABQ",
        "key": "5XabHkWEdc+5zB7D1EZfffnj2s6Lv17OXj+ml2TFlTI"
    },
    {
        "id": "AAAABA",
        "key": "4ngvDuLW7eH/NiTPY1vSS8Dl9ICqz0RpeSNWz91rqV8"
    },
    {
        "id": "AAAAAw",
        "key": "rCCgpJbAJKnQ6AvqjH4g/pff+YwqbY4mCmWQ/5ez2Ac"
    },
    {
        "id": "AAAAAg",
        "key": "/JVZwRJWlZQY2sIqLpfRdyDKW15/NZGLT7AYde2IrUs"
    },
    {
        "id": "AAAAAQ",
        "key": "nowJstVupM7pOX9xm9qedq/UncY/bn6AcUnZf7q5qVo"
    }
]
```

This payload should be published to the `POST /v1/identities/:id/pre_keys/` endpoint.

```go
func main() {
    // account generated via one of the above steps is the 'acc' variable
    ...

    // use 'olm_account_one_time_keys_length' to get the size of the output
    olen := C.olm_account_one_time_keys_length(acc)

    // allocate some memory for the olm account as a byte array.
	obuf := make([]byte, olen)

    // retrieve the one time keys using 'olm_account_one_time_keys'
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the address of the first byte in the allocated output byte array
    // 3. the size of the output byte array
	C.olm_account_one_time_keys(
		acc,
		unsafe.Pointer(&obuf[0]),
		olen,
	)

    // check the account for any errors when retrieving the one time keys
    // 'olm_account_last_error' returns a c string with an error message
    // detailing any error. it will return 'SUCCESS' if the operation completed
    lastError := C.olm_account_last_error(acc)
	if C.CString(lastError) == "SUCCESS" {
        // succeeded
    } else {
        // errored
    }
}
```

### Removing One Time Keys

When establishing a new encrypted session with another identity who has initiated
the line of communication, you will need to remove the prekey used for the message
from the account.

```go
func main() {
    // use 'olm_remove_one_time_keys' remove the prekey used for the  initial
    // message. details about creating sessions are found bellow.
    // parameters here are:
    // 1. the pointer to the account created above
    // 2. the pointer to the inbound olm session
    C.olm_remove_one_time_keys(acc, sess)
}
```


## Sessions

Sessions can be created to establish an encrypted line of communication with
another identity. Sessions allow you to encrypt or decrypt messages from another
identity.

For creating a session there are two options.
1. Create an `inbound session` when you have received a message from an identity
   that you have not sent or received messages to before.
2. Create an `outbound session` when you want to send a message to an identity
   that you have not sent or received messages to before.

Messages that you receive have two components, a type and the ciphertext.

Type denotes the kind of message. If the message type is `0`, it is an initial
message used to establish an encrypted session. If the type is `1`, it is a
normal message for use with an existing session.

The ciphertext is the base64 encoded encrypted payload of the message.

```json
{
    "mtype": 0,
    "ciphertext": "encrypted message payload"
}
```

This will be received as a subcomponent of a group message, which will resemble:

```json
{
    "recipients": {
        "alice:1": {
             "mtype": 0,
             "ciphertext": "encryptedKeyforCiphertextMessage"
         },
        "bob:1": {
             "mtype": 1,
             "ciphertext": "encryptedKeyforCiphertextMessage"
         },
    },
    "ciphertext": "ciphertextMessage"
}
```

When you receive a group message and you have no corresponding session for its
sender, you should deserialize the message and extract the messages `mtype` and
`ciphertext`.

If the messages `mtype` is equal to `0`, you should create a new inbound session.


### Inbound Session

When you have received a message from a previously unknown identity, you should
create an inbound session from the received message.

```go
func main() {
    // account generated via one of the above steps is the 'acc' variable
    ...

    // allocate some memory for the olm account as a byte array.
    // 'olm_account_size' will return the size needed for the array
    buf := make([]byte, C.olm_session_size())

    // pass in the allocated memory to `olm_session`. this will return a pointer
    // to the olm session. You should pass the address of the first byte of the
    // array you allocated above.
	sess := C.olm_session(unsafe.Pointer(&buf[0]))

    // byte array of the recipients message `ciphertext` as mentioned above
    mbuf := []byte(ciphertext)

    // use 'olm_create_inbound_session' to create a new session from the message
    // parameters here are:
    // 1. the pointer to the session created above
    // 2. the pointer to the account created above
    // 3. the address of the first byte in the recipients message ciphertext
    //    byte array
    // 4. the size of the recipients message ciphertext byte array
	C.olm_create_inbound_session(
		sess,
		acc,
		unsafe.Pointer(&mbuf[0]),
		C.size_t(len(mbuf)),
	)

    // check the session for any errors when creating the inbound session
    // 'olm_session_last_error' returns a c string with an error message
    // detailing any error. it will return 'SUCCESS' if the operation completed
    lastError := C.olm_session_last_error(acc)
	if C.CString(lastError) == "SUCCESS" {
        // succeeded
    } else {
        // errored
    }

    // use 'olm_remove_one_time_keys' remove the prekey used for the  initial
    // message.
    // 1. the pointer to the account created above
    // 2. the pointer to the session created above
    C.olm_remove_one_time_keys(acc, sess)
}
```

### Outbound Session

When you wish to create a session with a new recipient that you have not
communicated with before, you should create an outbound session.

To do this, you will need to retrieve the identity public key of a recipient from selfs
api. You will then need to convert this key to a curve25519 key and encode it
using non-padded standard base64 and pass it in as an argument.

In addition, you will need to retrieve a prekey for the recipients identity from
self's api. You can acquire a prekey through the `GET /identities/:id/pre_keys/`.

This will return:

```json
{
    "id": "AAAAAQ",
    "key": "nowJstVupM7pOX9xm9qedq/UncY/bn6AcUnZf7q5qVo"
}
```

```go
func main() {
    // account generated via one of the above steps is the 'acc' variable
    ...

    // allocate some memory for the olm account as a byte array.
    // 'olm_account_size' will return the size needed for the array
    buf := make([]byte, C.olm_session_size())

    // pass in the allocated memory to `olm_session`. this will return a pointer
    // to the olm session. You should pass the address of the first byte of the
    // array you allocated above.
	sess := C.olm_session(unsafe.Pointer(&buf[0]))

    // byte array of the curve25519 identity key
    ikbuf := []byte(identityKey)

    // byte array of the one time key
    otkbuf := []byte(oneTimeKey)

    // use 'olm_create_outbound_session_random_length'
    rlen := C.olm_create_outbound_session_random_length(sess)

    // allocate a byte array to hold the random data
	rbuf := make([]byte, rlen)

    // read some random data into the buffer
	_, err := rand.Read(rbuf)
	if err != nil {
		return nil, err
	}

    // use 'olm_create_outbound_session' to create an outbound session
    // parameters here are:
    // 1. the pointer to the session created above
    // 2. the pointer to the account created above
    // 3. the address of the first byte in the identity keys byte array
    // 4. the size of the identity keys byte array
    // 5. the address of the first byte in the one time keys byte array
    // 6. the size of the identitiy keys byte array
    // 7. the address of the first byte in the random buffer byte array
    // 8. the size of the random buffer byte array
	C.olm_create_outbound_session(
		sess,
		acc,
		unsafe.Pointer(&ikbuf[0]),
		C.size_t(len(ikbuf)),
		unsafe.Pointer(&otkbuf[0]),
		C.size_t(len(otkbuf)),
		unsafe.Pointer(&rbuf[0]),
		rlen,
	)

    // check the session for any errors when creating the outbound session
    // 'olm_session_last_error' returns a c string with an error message
    // detailing any error. it will return 'SUCCESS' if the operation completed
    lastError := C.olm_session_last_error(acc)
	if C.CString(lastError) == "SUCCESS" {
        // succeeded
    } else {
        // errored
    }
}
```

### Existing Session

If you have an existing session that you have stored, you can reload it as
follows:

```go
func main() {
    // account generated via one of the above steps is the 'acc' variable
    ...

    // allocate some memory for the olm account as a byte array.
    // 'olm_account_size' will return the size needed for the array
    buf := make([]byte, C.olm_session_size())

    // pass in the allocated memory to `olm_session`. this will return a pointer
    // to the olm session. You should pass the address of the first byte of the
    // array you allocated above.
	sess := C.olm_session(unsafe.Pointer(&buf[0]))

    // cast the encryption key string used to encrypt the session to a byte array
    kbuf := []byte(key)

    // cast the pickled/encoded session from a string to a byte array
	pbuf := []byte(pickle)

	// use 'olm_unpickle_session' to load the session
    // parameters here are:
    // 1. the pointer to the session created above
    // 2. the address of the first byte in the encryption keys byte array
    // 3. the size of the encryption keys byte array
    // 4. the address of the first byte in the encryption keys byte array
    // 5. the size of the encryption keys byte array
	C.olm_unpickle_session(
		sess,
		unsafe.Pointer(&kbuf[0]),
		C.size_t(len(kbuf)),
		unsafe.Pointer(&pbuf[0]),
		C.size_t(len(pbuf)),
	)

    // check the session for any errors when creating the outbound session
    // 'olm_session_last_error' returns a c string with an error message
    // detailing any error. it will return 'SUCCESS' if the operation completed
    lastError := C.olm_session_last_error(acc)
	if C.CString(lastError) == "SUCCESS" {
        // succeeded
    } else {
        // errored
    }
}
```

### Storing Session

To serialize a session to a format that can be stored, you can do:

```go
func main() {
    // session generated via one of the above steps is the 'sess' variable
    ...

    // cast the encryption key string used to encrypt the session to a byte array
    kbuf := []byte(key)

    // use 'olm_pickle_sesion_length' to get the size of the pickled session
    plen := C.olm_pickle_session_length(s.ptr)

    // allocate space for the pickled session
	pbuf := make([]byte, plen)

    // pickle the account with 'olm_pickle_session'
    // parameters here are:
    // 1. the pointer to the session created above
    // 2. the address of the first byte in the encryption keys byte array
    // 3. the size of the encryption keys byte array
    // 4. the address of the first byte in the encryption keys byte array
    // 5. the size of the encryption keys byte array
	C.olm_pickle_session(
		sess,
		unsafe.Pointer(&kbuf[0]),
		C.size_t(len(kbuf)),
		unsafe.Pointer(&pbuf[0]),
		C.size_t(len(pbuf)),
	)

    // check the session for any errors when creating the outbound session
    // 'olm_session_last_error' returns a c string with an error message
    // detailing any error. it will return 'SUCCESS' if the operation completed
    lastError := C.olm_session_last_error(acc)
	if C.CString(lastError) == "SUCCESS" {
        // succeeded
    } else {
        // errored
    }
}
```
