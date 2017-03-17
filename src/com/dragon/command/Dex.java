package com.dragon.command;

import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * @author kiva
 * @date 2017/3/17
 */
public class Dex {
    public static void main(String[] args) {
        DexConverter converter = new DexConverter();
        try {
            FileInputStream is = new FileInputStream("/Users/nullptr/Hello.class");
            byte[] bytes = new byte[is.available()];
            is.read(bytes);
            is.close();

            System.out.println("Calling convertJavaClass");
            byte[] out = converter.convertJavaClass("Hello", bytes);

            System.out.println("Output.");
            FileOutputStream os = new FileOutputStream("/Users/nullptr/Hello.class.dex");
            os.write(out);
            os.flush();
            os.close();

            System.out.println("Cleaning.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
