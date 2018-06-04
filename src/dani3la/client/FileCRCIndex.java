/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dani3la.client;

/**
 * Classe che implementa il metodo nativo (c++) della libreria FileCRCIndex.dll
 *
 * @author Stefano Fiordi
 */
public class FileCRCIndex {

    protected String path; // percorso del file
    protected int chunkSize; // dimensione dei pezzi
    protected long nChunks; // numero di pezzi
    protected long fileLength; // dimensione del file

    /**
     * Standard constructor
     *
     * @param path percorso del file
     * @param chunkSize dimensione dei pezzi
     * @param nChunks numero di pezzi
     * @param fileLength dimensione del file
     */
    public FileCRCIndex(String path, int chunkSize, long nChunks, long fileLength) {
        this.path = path;
        this.chunkSize = chunkSize;
        this.nChunks = nChunks;
        this.fileLength = fileLength;
    }

    /**
     * Caricamento della libreria FileCRCIndex.dll
     */
    static {
        System.loadLibrary("FileCRCIndex");
    }

    /**
     * Metodo in codice nativo (c++) che calcola i digest in CRC32 di un file
     * suddiviso in parti
     *
     * @param fileName percorso del file
     * @param chunkSize dimensione dei pezzi
     * @param nChunks numero di pezzi
     * @param fileLength dimensione del file
     * @return il vettore contenente i digest
     */
    private native long[] calculateFileCRC(String fileName, int chunkSize, long nChunks, long fileLength);

    /**
     * Metodo che richiama quello in codice nativo
     *
     * @return il vettore di digest se non nullo
     * @throws MyExc se il vettore di digest è nullo
     */
    public long[] calcDigests() throws MyExc {
        long[] digests = calculateFileCRC(path, chunkSize, nChunks, fileLength);

        if (digests == null) {
            throw new MyExc("CRC indexing error");
        }

        return digests;
    }
}
