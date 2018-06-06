/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dani3la.client;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 *
 * @author Stefano Fiordi
 */
public class NetChoose extends JPanel {

    Enumeration<NetworkInterface> nets;
    JRadioButton[] rButtons;

    public NetChoose(Enumeration<NetworkInterface> nets) {
        super(new BorderLayout());
        JPanel radioPanel = new JPanel(new GridLayout(0, 1));
        ButtonGroup group = new ButtonGroup();
        this.nets = nets;
        rButtons = new JRadioButton[0];
        for (NetworkInterface netint : Collections.list(this.nets)) {
            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            int count = 0;
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                count++;
            }
            if (count > 0) {
                realloc(rButtons.length + 1);
                rButtons[rButtons.length - 1] = new JRadioButton(netint.getInterfaceAddresses().get(0).getAddress().getHostAddress());
                rButtons[rButtons.length - 1].setSelected(true);
                group.add(rButtons[rButtons.length - 1]);
                radioPanel.add(rButtons[rButtons.length - 1]);
            }
        }
        
        add(radioPanel, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
    }
    
    private void realloc(int newLength) {
        JRadioButton[] newArray = new JRadioButton[newLength];
        if (newLength >= rButtons.length) {
            System.arraycopy(rButtons, 0, newArray, 0, rButtons.length);
        } else {
            System.arraycopy(rButtons, 0, newArray, 0, newLength);
        }
        
        rButtons = newArray;
    }
    
    protected void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame("Scelta rete");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Create and set up the content pane.
        JComponent newContentPane = new NetChoose(nets);
        newContentPane.setOpaque(true);
        frame.setContentPane(newContentPane);

        //Display the window.
        frame.pack();
        frame.setVisible(true);
    }

}
