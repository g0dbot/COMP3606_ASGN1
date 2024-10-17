package com.example.comp3606a1.encryption
import java.security.MessageDigest
import kotlin.text.Charsets.UTF_8
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.SecretKey
import javax.crypto.Cipher
import kotlin.io.encoding.Base64
import kotlin.random.Random

//blh

interface Authenticator{

}

@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
class Encryption {
    //helper functions
    private fun ByteArray.toHex() = joinToString(separator = "") { byte -> "%02x".format(byte)}

    private fun getFirstNChars(str: String, n: Int) = str.substring(0, n)

    //gen strong seed
    private fun hashStrSha256(str: String): String{
        val algorithm = "SHA-256"
        val hashedString = MessageDigest.getInstance(algorithm).digest(str.toByteArray(UTF_8))
        return hashedString.toHex()
    }

    //gen AES Key
    private fun generateAESKey(seed: String): SecretKeySpec {
        val first32Chars = getFirstNChars(seed, 32)
        val secretKey = SecretKeySpec(first32Chars.toByteArray(), "AES")
        return secretKey
    }

    //gen AES IV
    private fun generateIV(seed: String): IvParameterSpec {
        val first16Chars = getFirstNChars(seed, 16)
        return IvParameterSpec(first16Chars.toByteArray())
    }

    //encrypt data
    private fun encryptMessage(plaintext: String, aesKey: SecretKey, aesIv: IvParameterSpec): String {
        val plainTextByteArr = plaintext.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, aesIv)

        val encrypt = cipher.doFinal(plainTextByteArr)
        return Base64.Default.encode(encrypt)
    }

    //decrypt data
    private fun decryptMessage(encryptedText: String, aesKey:SecretKey, aesIV: IvParameterSpec): String{
        val textToDecrypt = Base64.Default.decode(encryptedText)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5PADDING")

        cipher.init(Cipher.DECRYPT_MODE, aesKey, aesIV)

        val decrypt = cipher.doFinal(textToDecrypt)

        return String(decrypt)
    }

    //challenge resp protocol
    //gen rand num
    private fun genRandomNum(): Int {
        return Random.nextInt()
    }

    //student encrptyion proto
    fun studentResponse(randomNumber: String, studentID: String): String {
        val studentIDHash = hashStrSha256(studentID)
        val aesKey = generateAESKey(studentIDHash)
        val aesIV = generateIV(studentIDHash)

        return encryptMessage(randomNumber, aesKey, aesIV)
    }

    //for lecturer to verify student
    fun verifyResponse(encryptedResponse: String, randomNumber: String, studentID: String): Boolean {
        val studentIDHash = hashStrSha256(studentID)
        val aesKey = generateAESKey(studentIDHash)
        val aesIV = generateIV(studentIDHash)

        val decryptedResponse = decryptMessage(encryptedResponse, aesKey, aesIV)
        return decryptedResponse == randomNumber
    }

    //chall resp
    fun authenticateStudent(studentID: String): Boolean {
        // Lecturer sends a random number to the student
        val randomNumber = genRandomNum()

        // Student encrypts the random number with their StudentID
        val encryptedResponse = studentResponse(randomNumber, studentID)

        // Lecturer verifies the student's response
        return verifyResponse(encryptedResponse, randomNumber, studentID)
    }
}