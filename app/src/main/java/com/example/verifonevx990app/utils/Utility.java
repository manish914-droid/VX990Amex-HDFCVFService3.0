package com.example.verifonevx990app.utils;

import android.util.Log;

import com.example.verifonevx990app.emv.transactionprocess.CardProcessedDataModal;

/**
 * Created by Simon on 2018/8/31.
 * Utility for BCD ASCII convert and others
 */

public class Utility {


    public static String byte2HexStr(byte[] var0, int offset, int length ) {
        if (var0 == null) {
            return "";
        } else {
            String var1;
            StringBuilder var2 = new StringBuilder();

            for (int var3 = offset; var3 < (offset+length); ++var3) {
                var1 = Integer.toHexString(var0[var3] & 255);
                var2.append(var1.length() == 1 ? "0" + var1 : var1);
            }

            return var2.toString().toUpperCase().trim();
        }
    }
    public static String byte2HexStr(byte[] var0) {
        if (var0 == null) {
            return "";
        } else {
            String var1;
            StringBuilder var2 = new StringBuilder();

            for (byte b : var0) {
                var1 = Integer.toHexString(b & 255);
                var2.append(var1.length() == 1 ? "0" + var1 : var1);
            }

            return var2.toString().toUpperCase().trim();
        }
    }


    public static byte[] hexStr2Byte(String hexString) {
//        Log.d(TAG, "hexStr2Byte:" + hexString);
        if (hexString == null || hexString.length() == 0 ) {
            return new byte[] {0};
        }
        String hexStrTrimed = hexString.replace(" ", "");
//        Log.d(TAG, "hexStr2Byte:" + hexStrTrimed);
        {
            String hexStr = hexStrTrimed;
            int len = hexStrTrimed.length();
            if( (len % 2 ) == 1 ){
                hexStr = hexStrTrimed + "0";
                ++len;
            }
            char highChar, lowChar;
            int high, low;
            byte[] result = new byte[len / 2];
            String s;
            for( int i=0; i< hexStr.length(); i++ ) {
                // read 2 chars to convert to byte
//                s = hexStr.substring(i,i+2);
//                int v = Integer.parseInt(s, 16);
//
//                result[i/2] = (byte) v;
//                i++;
                // read high byte and low byte to convert
                highChar = hexStr.charAt(i);
                lowChar = hexStr.charAt(i+1);
                high = CHAR2INT(highChar);
                low = CHAR2INT(lowChar);
                result[i/2] = (byte) (high*16+low);
                i++;
            }
            return  result;

        }
    }
    public static byte[] hexStr2Byte(String hexString, int beginIndex, int length ) {
        if (hexString == null || hexString.length() == 0 ) {
            return new byte[] {0};
        }
        {
            if( length > hexString.length() ){
                length = hexString.length();
            }
            String hexStr = hexString;
            int len = length;
            if( (len % 2 ) == 1 ){
                hexStr = hexString + "0";
                ++len;
            }
            byte[] result = new byte[len / 2];
            String s;
            for( int i=beginIndex; i< len; i++ ) {
                s = hexStr.substring(i,i+2);
                int v = Integer.parseInt(s, 16);

                result[i/2] = (byte) v;
                i++;
            }
            return  result;

        }
    }

    public static byte HEX2DEC( int hex ){
        return (byte)((hex/10)*16+hex%10);
    }

    public static int DEC2INT( byte dec ){
        int high = ((0x007F & dec) >> 4);
        if( 0!= (0x0080&dec) ) {
            high += 8;
        }
        return (high)*10+(dec&0x0F) ;
    }
    public static int CHAR2INT(char c) {
        if(
                (c >= '0' && c <= '9' )
            || (c == '=' )  // for track2
                ) {
            return c - '0';
        } else if(c >= 'a' && c <= 'f'){
            return c - 'a' +10;
        } else if(c >= 'A' && c <= 'F'){
            return c - 'A' +10;
        } else {
            return 0;
        }
    }

    public static void getCardHolderName(CardProcessedDataModal cardProcessedDataModal, String str, char starname, char endName) {
        // Length of string
        if (null != str && !str.isEmpty()) {
            int N = str.length();
            int start = 0;
            int end = N - 1;
            int startindex = 0;
            int endIndex = 0;


            while (true) {
                if (null != str && !(str.isEmpty()) && str.charAt(start) == starname) {
                    startindex = start;
                    break;
                }
                start++;
            }
            while (true) {
                if (null != str && !(str.isEmpty()) && str.charAt(end) == endName) {
                    endIndex = end;
                    break;
                }
                end--;
            }
            if (null != str && !str.isEmpty()) {
                cardProcessedDataModal.setCardHolderName(str.substring(startindex + 1, endIndex));
                //System.out.println("CardHoder Name is" + str.substring(startindex + 1, endIndex));
                //Logging card name
                Log.e("Card Holder Name ---> ", cardProcessedDataModal.getCardHolderName());
            }

        }

    }

}
