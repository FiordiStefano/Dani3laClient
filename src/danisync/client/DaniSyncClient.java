/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danisync.client;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import static javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE;

/**
 *
 * @author Stefano Fiordi
 */
public class DaniSyncClient extends JFrame {

    public DaniSyncClient() throws HeadlessException {
        int width = (Toolkit.getDefaultToolkit().getScreenSize().width * 34) / 100;
        int heigth = (Toolkit.getDefaultToolkit().getScreenSize().height * 30) / 100;
        int x = (Toolkit.getDefaultToolkit().getScreenSize().width * 33) / 100;
        int y = (Toolkit.getDefaultToolkit().getScreenSize().height * 20) / 100;
        super.setBounds(x, y, width, heigth);
        super.setResizable(false);
        super.setTitle("DaniSync - Client");
        super.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        super.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (JOptionPane.showConfirmDialog(DaniSyncClient.this, "Sei sicuro di voler uscire?", "Esci", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION) {
                    System.exit(0);
                }
            }
        });
    }
    
    

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        new DaniSyncClient().setVisible(true);
        /*File newVersion = new File("E:/dati/Download/ubuntu-17.10.1-desktop-amd64.iso");
        try {
            FileHandlerClient fhc = new FileHandlerClient(newVersion, 1024 * 1024);
            fhc.FileIndexing();
            System.out.println("Version: " + fhc.version);
        } catch (IOException | MyExc ex) {
            System.err.println("Error: " + ex.getMessage());
        }

        try {
            Socket socket = new Socket("127.0.0.1", 6365);

        } catch (IOException ex) {
            System.err.println("Error: " + ex.getMessage());
        }*/
    }

}
