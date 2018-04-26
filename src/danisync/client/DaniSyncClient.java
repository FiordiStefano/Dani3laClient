/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danisync.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 *
 * @author Stefano Fiordi
 */
public class DaniSyncClient {

    /**
     * Crea il digest CRC32 di un array binario
     *
     * @param packet array binario
     * @return il digest
     * @throws NoSuchAlgorithmException
     */
    static long CRC32Hashing(byte[] packet) {
        Checksum checksum = new CRC32();
        checksum.update(packet, 0, packet.length);

        return checksum.getValue();
    }

    /**
     * Crea il chunk attraverso un buffer binario
     *
     * @param buf buffer binario
     * @return il chunk array binario
     */
    static byte[] createChunk(ByteBuffer buf) {
        buf.flip();
        byte[] chunk = new byte[buf.remaining()];
        buf.get(chunk);
        buf.clear();

        return chunk;
    }

    /**
     * Scrive su un file indice tutti i digest
     *
     * @param digests array di digest da scrivere sul file
     * @param filename il nome del file indice
     * @throws IOException se ci sono errori durante la scrittura
     */
    public static void writeDigests(long[] digests, String filename) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename, false));
        for (long l : digests) {
            String s = Long.toString(l);
            while (s.length() < 10) {
                s = "0" + s;
            }
            writer.write(s);
        }

        writer.close();
    }

    /**
     * Legge i digest da un file indice
     *
     * @param filename il nome del file indice
     * @param chunks il numero di digest da leggere
     * @return l'array contenente i digest letti
     * @throws IOException se ci sono errori durante la lettura
     * @throws NumberFormatException se il digest letto presenta caratteri
     * differenti da numeri
     */
    public static long[] readDigests(String filename, int chunks) throws IOException, NumberFormatException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        char[] s = new char[10];
        long[] digests = new long[chunks];
        int len;
        for (int i = 0; (len = reader.read(s)) != -1; i++) {
            digests[i] = Long.parseLong(new String(s));
        }
        reader.close();

        return digests;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        File newVersion = new File("E:/dati/Download/ubuntu-17.10.1-desktop-amd64.iso");
        try {
            FileHandlerClient fhc = new FileHandlerClient(newVersion, 1024 * 1024);
            fhc.FileIndexing();
            System.out.println("Version: " + fhc.version);
        } catch (IOException | MyExc ex) {
            System.err.println("Error: " + ex.getMessage());
        }
        /*File newVersion = new File("E:/vdis/FSV 2.vdi");
        //File newVersion = new File("E:/dati/Download/ubuntu-17.10.1-desktop-amd64.iso");
        long newChunks, ver;
        final int ChunkSize = 1024 * 1024;
        long[] newDigests;
        
        if (newVersion.length() % ChunkSize == 0) {
            newChunks = newVersion.length() / ChunkSize;
        } else {
            newChunks = newVersion.length() / ChunkSize + 1;
        }

        try {
            File crcIndexes = new File(newVersion.getName() + ".crc");
            if (!crcIndexes.exists()) {
                FileCRCIndex fciNew = new FileCRCIndex(newVersion.getAbsolutePath(), ChunkSize, newChunks, newVersion.length());

                System.out.println("CRC indexing started...");
                newDigests = fciNew.calcDigests();

                System.out.println("Writing " + crcIndexes.getName() + "...");
                writeDigests(newDigests, newVersion.getName() + ".crc");
                System.out.println("Success");
            } else {
                System.out.println("Reading indexes...");
                newDigests = readDigests(newVersion.getName() + ".crc", (int) newChunks);
                System.out.println("Success");
                for (long l : newDigests) {
                    System.out.println(l);
                }
            }

            ver = CRC32Hashing(Files.readAllBytes(crcIndexes.toPath()));
            System.out.println("Version: " + ver);

            try {
                Socket socket = new Socket("127.0.0.1", 6365);
                

            } catch (IOException ex) {
                System.err.println("Error: " + ex.getMessage());
            }
        } catch (MyExc | IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }*/
    }

}
