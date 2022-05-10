package com.fileuploader;

import java.security.*;
import java.util.Base64;
import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;


public class Encryptor {
	private static final String ALGO="AES";
	
	private static final byte[] keyValue= new byte[]{
			'!','*','-','&','^','9','1','7','8','2','(',')','+','=','%','%'
	};
	
	public static String Encrypt(String plainText)throws Exception{
		Key key= generateKey();
		
		Cipher c= Cipher.getInstance(ALGO);
		c.init(Cipher.ENCRYPT_MODE, key);
		byte[] encVal= c.doFinal(plainText.getBytes());
		String encryptedValue= Base64.getEncoder().encodeToString(encVal);
		
		return encryptedValue;
	}
	
	public static String Decrypt(String cipherText)
			throws Exception{
		Key key= generateKey();
		
		Cipher c= Cipher.getInstance(ALGO);
		c.init(Cipher.DECRYPT_MODE, key);
		
		byte[] decodedValue= Base64.getDecoder().decode(cipherText);
		
		byte[] decValue= c.doFinal(decodedValue);
		
		String decryptedValue= new String(decValue);
		
		return decryptedValue;
	}
	
	private static Key generateKey()throws Exception{
		Key key= new SecretKeySpec(keyValue, ALGO);
		
		return key;
	}

}
