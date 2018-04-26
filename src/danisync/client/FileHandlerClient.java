/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danisync.client;

import com.google.protobuf.ByteString;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.Checksum;
import packet.protoPacket.data;
import packet.protoPacket.info;

/**
 *
 * @author Stefano Fiordi
 */
public class FileHandlerClient {

    /**
     * Dimensione dei pacchetti
     */
    private final int PacketLength = 4096;
    /**
     * File da sincronizzare
     */
    protected File ClientFile;
    /**
     * Numero di pacchetti
     */
    protected long nPackets;
    /**
     * Canale di lettura del file
     */
    protected FileChannel fcClient;
    /**
     * Dimensione dei pezzi
     */
    protected int ChunkSize;
    /**
     * Numero di pezzi
     */
    protected long nChunks;
    /**
     * Array di digest crc
     */
    protected long[] digests;
    /**
     * File indice contenente i digest crc
     */
    protected File crcIndex;
    /**
     * Versione del file determinata dal crc calcolato sul file indice
     */
    protected long version;

    /**
     * Costruttore che crea il FileChannel di lettura sul file, calcola il
     * numero di pacchetti e di pezzi in base alle dimensioni del file e, se
     * esiste, legge il file indice contenente i digest e la versione del file
     *
     * @param ClientFile il file da sincronizzare
     * @param ChunkSize la grandezza dei pezzi
     * @throws IOException se la creazione del canale non va a buon fine
     * @throws MyExc se c'è un errore nella lettura della versione
     */
    public FileHandlerClient(File ClientFile, int ChunkSize) throws IOException, MyExc {
        this.ClientFile = ClientFile;
        this.ChunkSize = ChunkSize;
        this.fcClient = new FileInputStream(this.ClientFile).getChannel();

        if (this.ClientFile.length() % 2 == 0) {
            nPackets = this.ClientFile.length() / PacketLength;
            nChunks = this.ClientFile.length() / this.ChunkSize;
        } else {
            nPackets = this.ClientFile.length() / PacketLength + 1;
            nChunks = this.ClientFile.length() / this.ChunkSize + 1;
        }

        this.crcIndex = new File(this.ClientFile.getName() + ".crc");
        if (this.crcIndex.exists()) {
            readDigests();
        }
    }

    /**
     * Crea il pacchetto contenente le informazioni sul file da inviare
     *
     * @return il pacchetto informazioni
     */
    protected info getInfoPacket() {
        return info.newBuilder()
                .setNam(ClientFile.getName())
                .setLen(ClientFile.length())
                .setVer(version)
                .build();
    }

    /**
     * Metodo che crea il pacchetto dati
     *
     * @param packetIndex il numero del pacchetto
     * @param packet l'array binario
     * @return il pacchetto dati
     */
    protected data createPacket(int packetIndex, byte[] packet) {

        return data.newBuilder()
                .setNum(packetIndex)
                .setDat(ByteString.copyFrom(packet))
                .build();
    }

    /**
     * Metodo che costruisce il pacchetto
     *
     * @param packetIndex il numero del pacchetto
     * @return il pacchetto da inviare
     * @throws IOException se si verifica un errore di lettura
     * @throws MyExc se si verifica un errore di lettura
     */
    public data buildPacket(int packetIndex) throws IOException, MyExc {
        ByteBuffer buf = ByteBuffer.allocate(PacketLength);
        int len;
        if ((len = fcClient.read(buf, (long) packetIndex * PacketLength)) != -1) {
            return createPacket(packetIndex, getByteArray(buf));
        } else {
            throw new MyExc("Error while reading packet from file");
        }
    }

    /**
     * Calcola i digest e li scrive su un file .crc
     *
     * @throws IOException se ci sono errori nella scrittura dei digest sul file
     * .crc
     * @throws MyExc se il calcolo non va a buon fine
     */
    protected void FileIndexing() throws IOException, MyExc {
        FileCRCIndex fciClient = new FileCRCIndex(ClientFile.getAbsolutePath(), ChunkSize, nChunks, ClientFile.length());
        digests = fciClient.calcDigests();
        writeDigests();
    }

    /**
     * Crea il digest CRC32 di un array binario
     *
     * @param packet array binario
     * @return il digest
     * @throws NoSuchAlgorithmException
     */
    private long CRC32Hashing(byte[] packet) {
        Checksum checksum = new CRC32();
        checksum.update(packet, 0, packet.length);

        return checksum.getValue();
    }

    /**
     * Trasforma un buffer binario in un array binario
     *
     * @param buf buffer binario
     * @return l'array binario
     */
    private byte[] getByteArray(ByteBuffer buf) {
        buf.flip();
        byte[] chunk = new byte[buf.remaining()];
        buf.get(chunk);
        buf.clear();

        return chunk;
    }

    /**
     * Scrive su un file indice tutti i digest
     *
     * @throws IOException se ci sono errori durante la scrittura
     */
    private void writeDigests() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(crcIndex, false));
        String s;
        for (long l : digests) {
            s = Long.toString(l);
            while (s.length() < 10) {
                s = "0" + s;
            }
            writer.write(s);
        }

        writer.close();

        writer = new BufferedWriter(new FileWriter(crcIndex, true));
        version = CRC32Hashing(Files.readAllBytes(crcIndex.toPath()));
        s = Long.toString(version);
        while (s.length() < 10) {
            s = "0" + s;
        }
        writer.write(s);
        writer.close();
    }

    /**
     * Legge i digest da un file indice
     *
     * @throws IOException se ci sono errori durante la lettura
     * @throws NumberFormatException se il digest letto presenta caratteri
     * differenti da numeri
     * @throws MyExc se la versione non viene letta correttamente
     */
    private void readDigests() throws IOException, NumberFormatException, MyExc {
        BufferedReader reader = new BufferedReader(new FileReader(crcIndex));
        char[] s = new char[10];
        digests = new long[(int) nChunks];
        int len;
        for (int i = 0; i < (int) nChunks; i++) {
            if ((len = reader.read(s)) != -1) {
                digests[i] = Long.parseLong(new String(s));
            }
        }

        if ((len = reader.read(s)) != -1) {
            version = Long.parseLong(new String(s));
        } else {
            throw new MyExc("Error while reading file version");
        }
        reader.close();
    }
}
