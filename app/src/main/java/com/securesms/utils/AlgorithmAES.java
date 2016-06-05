package com.securesms.utils;

import android.content.Context;
import android.telephony.SmsManager;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.spec.AlgorithmParameterSpec;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.securesms.R;
import com.securesms.database.DbAdapter;
import com.securesms.chat.model.MessageModel;
import com.securesms.contacts.model.ReceiverUserModel;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Created by Sebastian Sokolowski on 2015-07-08
 */
public class AlgorithmAES {
    public static AlgorithmAES INSTANCE;
    private Cipher cipher;
    private SecretKeySpec key;
    private AlgorithmParameterSpec spec;

    private final DbAdapter dbAdapter = DbAdapter.INSTANCE;

    private AlgorithmAES() {
    }

    public static void initInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AlgorithmAES();
        }
    }

    public void setPassword(String password) throws Exception {
        // hash password with SHA-256 and crop the output to 128-bit for key
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(password.getBytes("UTF-8"));
        byte[] keyBytes = new byte[32];
        System.arraycopy(digest.digest(), 0, keyBytes, 0, keyBytes.length);

        cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
        key = new SecretKeySpec(keyBytes, "AES");
        spec = getIV();
    }

    public AlgorithmParameterSpec getIV() {
        byte[] iv = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,};
        IvParameterSpec ivParameterSpec;
        ivParameterSpec = new IvParameterSpec(iv);

        return ivParameterSpec;
    }

    public String encrypt(String plainText) throws Exception {
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
        String encryptedText = new String(Base64.encode(encrypted, Base64.DEFAULT), "UTF-8");
        System.out.println("Encrypt Data" + encryptedText);
        return encryptedText;
    }

    public String decrypt(String cryptedText) throws Exception {
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        byte[] bytes = Base64.decode(cryptedText, Base64.DEFAULT);
        byte[] decrypted = cipher.doFinal(bytes);
        String decryptedText = new String(decrypted, "UTF-8");
        System.out.println("Encrypt Data" + decryptedText);
        return decryptedText;
    }

    public void send_message(String number, String message) {
        dbAdapter.open();
        ReceiverUserModel receiver_item = dbAdapter.searchRowReceiverNumber(number);

        try {
            this.setPassword(receiver_item.getPassword());

            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, "!encsms" + encrypt(message), null, null);
            //Toast.makeText(context, context.getString(R.string.send_sms), Toast.LENGTH_LONG).show();

            //zapisanie wiadomosci do bazy danych
            MessageModel tmp = new MessageModel();
            tmp.text = message;
            tmp.rec = 1;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            tmp.date = sdf.format(new Date());
            tmp.id_receivers = receiver_item.getId();
            tmp.read = 0;
            dbAdapter.createRowMessage(tmp);
        } catch (Exception e) {
            e.printStackTrace();
            //Toast.makeText(context, context.getString(R.string.error_send_sms), Toast.LENGTH_LONG).show();
        } finally {
            dbAdapter.close();
        }
    }

    public String receive_message(ReceiverUserModel receiverUserModel, String message, Context context) {

        MessageModel tmp = new MessageModel();
        try {
            this.setPassword(receiverUserModel.getPassword());
            tmp.rec = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            tmp.date = sdf.format(new Date());
            tmp.id_receivers = receiverUserModel.getId();
            tmp.read = 1;
            tmp.text = this.decrypt(message);
        } catch (Exception e) {
            tmp.text = context.getString(R.string.error_desc_sms);
            e.printStackTrace();
        } finally {
            dbAdapter.open();
            dbAdapter.createRowMessage(tmp);
            dbAdapter.close();
        }
        return tmp.text;
    }

}