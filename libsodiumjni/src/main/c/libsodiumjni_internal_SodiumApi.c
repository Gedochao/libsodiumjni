#include <stdlib.h>
#include <sodium.h>
#include "libsodiumjni_internal_SodiumApi.h"

#ifdef __cplusplus
extern "C" {
#endif

/* Returned when passed arrays or lengths that are inconsistent with each other or with libsodium's
 * constants. Every length is checked before any native memory access, so that invalid arguments
 * can't make libsodium read or write out of bounds. */
#define LIBSODIUMJNI_INVALID_ARGUMENTS -10

/* Returned along with a pending Java exception (NullPointerException, OutOfMemoryError…), in which
 * case the JVM throws that exception and ignores the returned value. */
#define LIBSODIUMJNI_EXCEPTION_THROWN -11

/*
 * Array contents are copied with Get/SetByteArrayRegion, from / to buffers allocated here, rather
 * than accessed via GetByteArrayElements: the latter can return NULL (when the JVM can't allocate a
 * copy), and these buffers are needed anyway to pass sizes libsodium can rely on.
 */

static void throw_new(JNIEnv *env, const char *class_name, const char *message) {
    jclass cls = (*env)->FindClass(env, class_name);
    /* if cls is NULL, FindClass already left an exception pending (e.g. NoClassDefFoundError) */
    if (cls != NULL) {
        (*env)->ThrowNew(env, cls, message);
        (*env)->DeleteLocalRef(env, cls);
    }
}

/* Throws a NullPointerException with the passed message and returns 1 if array is NULL */
static int is_null(JNIEnv *env, jbyteArray array, const char *message) {
    if (array == NULL) {
        throw_new(env, "java/lang/NullPointerException", message);
        return 1;
    }
    return 0;
}

/* malloc throwing an OutOfMemoryError when it fails, and never called with a size of 0 (for which
 * malloc may return NULL, which would be mistaken for a failure) */
static unsigned char *checked_malloc(JNIEnv *env, size_t size) {
    unsigned char *ptr = malloc(size > 0 ? size : 1);
    if (ptr == NULL)
        throw_new(env, "java/lang/OutOfMemoryError", "Cannot allocate native memory for libsodium");
    return ptr;
}

/* Wipes a buffer that held secret data (plaintext, secret key), before it's freed or goes out of
 * scope. Unlike memset, sodium_memzero can't be optimized away by the compiler. */
static void wipe(void *buf, size_t len) {
    sodium_memzero(buf, len);
}

/* Frees a buffer from checked_malloc that held secret data, after wiping it */
static void free_wiped(unsigned char *buf, size_t len) {
    wipe(buf, len > 0 ? len : 1);
    free(buf);
}

JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_sodium_1init
  (JNIEnv *env, jclass cls) {
    return sodium_init();
}


JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_crypto_1box_1public_1key_1bytes
  (JNIEnv *env, jclass cls) {
    return crypto_box_PUBLICKEYBYTES;
}

JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_crypto_1box_1secret_1key_1bytes
  (JNIEnv *env, jclass cls) {
    return crypto_box_SECRETKEYBYTES;
}

JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_crypto_1box_1keypair
  (JNIEnv *env, jclass cls, jbyteArray pub_key, jbyteArray secret_key) {

    if (is_null(env, pub_key, "public key is null") ||
        is_null(env, secret_key, "secret key is null"))
        return LIBSODIUMJNI_EXCEPTION_THROWN;

    jsize provided_pub_key_len = (*env)->GetArrayLength(env, pub_key);
    jsize provided_sec_key_len = (*env)->GetArrayLength(env, secret_key);

    if (provided_pub_key_len < crypto_box_PUBLICKEYBYTES || provided_sec_key_len < crypto_box_SECRETKEYBYTES)
        return LIBSODIUMJNI_INVALID_ARGUMENTS;

    unsigned char recipient_pk[crypto_box_PUBLICKEYBYTES];
    unsigned char recipient_sk[crypto_box_SECRETKEYBYTES];

    int res = crypto_box_keypair(recipient_pk, recipient_sk);

    if (res == 0) {
        (*env)->SetByteArrayRegion(env, pub_key, 0, crypto_box_PUBLICKEYBYTES, (const jbyte *) recipient_pk);
        (*env)->SetByteArrayRegion(env, secret_key, 0, crypto_box_SECRETKEYBYTES, (const jbyte *) recipient_sk);
    }

    wipe(recipient_sk, sizeof recipient_sk);
    return res;
}

JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_crypto_1box_1seal_1bytes
  (JNIEnv *env, jclass cls) {
    return crypto_box_SEALBYTES;
}

JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_crypto_1box_1seal
  (JNIEnv *env, jclass cls, jbyteArray cipher, jbyteArray msg, jlong msg_len, jbyteArray pub_key) {

    if (is_null(env, cipher, "cipher is null") ||
        is_null(env, msg, "message is null") ||
        is_null(env, pub_key, "public key is null"))
        return LIBSODIUMJNI_EXCEPTION_THROWN;

    jsize cipher_len = (*env)->GetArrayLength(env, cipher);
    jsize msg_array_len = (*env)->GetArrayLength(env, msg);
    jsize pub_key_len = (*env)->GetArrayLength(env, pub_key);

    /* msg_len <= msg_array_len (a jsize) ensures crypto_box_SEALBYTES + msg_len doesn't overflow */
    if (msg_len < 0 || msg_len > msg_array_len ||
        pub_key_len != crypto_box_PUBLICKEYBYTES ||
        cipher_len < crypto_box_SEALBYTES + msg_len)
        return LIBSODIUMJNI_INVALID_ARGUMENTS;

    jsize sealed_len = (jsize) (crypto_box_SEALBYTES + msg_len);

    unsigned char pub_key_bytes[crypto_box_PUBLICKEYBYTES];
    (*env)->GetByteArrayRegion(env, pub_key, 0, crypto_box_PUBLICKEYBYTES, (jbyte *) pub_key_bytes);

    unsigned char *msg_bytes = checked_malloc(env, (size_t) msg_len);
    if (msg_bytes == NULL)
        return LIBSODIUMJNI_EXCEPTION_THROWN;
    unsigned char *cipher_bytes = checked_malloc(env, (size_t) sealed_len);
    if (cipher_bytes == NULL) {
        free_wiped(msg_bytes, (size_t) msg_len);
        return LIBSODIUMJNI_EXCEPTION_THROWN;
    }

    (*env)->GetByteArrayRegion(env, msg, 0, (jsize) msg_len, (jbyte *) msg_bytes);

    int res = crypto_box_seal(cipher_bytes, msg_bytes, (unsigned long long) msg_len, pub_key_bytes);

    if (res == 0) {
        /* only copy what crypto_box_seal wrote, not uninitialized memory past it */
        (*env)->SetByteArrayRegion(env, cipher, 0, sealed_len, (const jbyte *) cipher_bytes);
    }

    free(cipher_bytes);
    free_wiped(msg_bytes, (size_t) msg_len);
    return res;
}

JNIEXPORT jint JNICALL Java_libsodiumjni_internal_SodiumApi_crypto_1box_1seal_1open
  (JNIEnv *env, jclass cls, jbyteArray decrypted, jbyteArray cipher, jint cipherLen, jbyteArray pub_key, jbyteArray secret_key) {

    if (is_null(env, decrypted, "decrypted is null") ||
        is_null(env, cipher, "cipher is null") ||
        is_null(env, pub_key, "public key is null") ||
        is_null(env, secret_key, "secret key is null"))
        return LIBSODIUMJNI_EXCEPTION_THROWN;

    jsize msg_len = (*env)->GetArrayLength(env, decrypted);
    jsize cipher_len = (*env)->GetArrayLength(env, cipher);
    jsize pub_key_len = (*env)->GetArrayLength(env, pub_key);
    jsize sec_key_len = (*env)->GetArrayLength(env, secret_key);

    /* crypto_box_seal_open writes exactly cipher_len - crypto_box_SEALBYTES bytes */
    if (cipher_len < crypto_box_SEALBYTES ||
        msg_len != cipher_len - crypto_box_SEALBYTES ||
        pub_key_len != crypto_box_PUBLICKEYBYTES ||
        sec_key_len != crypto_box_SECRETKEYBYTES)
        return LIBSODIUMJNI_INVALID_ARGUMENTS;

    unsigned char pub_key_bytes[crypto_box_PUBLICKEYBYTES];
    unsigned char sec_key_bytes[crypto_box_SECRETKEYBYTES];
    (*env)->GetByteArrayRegion(env, pub_key, 0, crypto_box_PUBLICKEYBYTES, (jbyte *) pub_key_bytes);
    (*env)->GetByteArrayRegion(env, secret_key, 0, crypto_box_SECRETKEYBYTES, (jbyte *) sec_key_bytes);

    unsigned char *cipher_bytes = checked_malloc(env, (size_t) cipher_len);
    if (cipher_bytes == NULL) {
        wipe(sec_key_bytes, sizeof sec_key_bytes);
        return LIBSODIUMJNI_EXCEPTION_THROWN;
    }
    unsigned char *msg = checked_malloc(env, (size_t) msg_len);
    if (msg == NULL) {
        free(cipher_bytes);
        wipe(sec_key_bytes, sizeof sec_key_bytes);
        return LIBSODIUMJNI_EXCEPTION_THROWN;
    }

    (*env)->GetByteArrayRegion(env, cipher, 0, cipher_len, (jbyte *) cipher_bytes);

    int res = crypto_box_seal_open(msg, cipher_bytes, (unsigned long long) cipher_len, pub_key_bytes, sec_key_bytes);

    if (res == 0) {
        (*env)->SetByteArrayRegion(env, decrypted, 0, msg_len, (const jbyte *) msg);
    }

    free_wiped(msg, (size_t) msg_len);
    free(cipher_bytes);
    wipe(sec_key_bytes, sizeof sec_key_bytes);
    return res;
}

#ifdef __cplusplus
}
#endif
