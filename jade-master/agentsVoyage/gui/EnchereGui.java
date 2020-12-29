package gui;

import agents.AlertAgent;
import agents.EnchereAgent;
import jade.gui.GuiEvent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Journey alert Gui, communication with AlertAgent throw GuiEven
 */
@SuppressWarnings("serial")
public class EnchereGui extends JFrame {
	/** Text area */
	private JTextArea jTextArea;

	private EnchereAgent myAgent;

	private String departure;
	private String arrival;

	public EnchereGui(EnchereAgent a) {
		this.setBounds(10, 10, 600, 200);

		myAgent = a;
		if (a != null)
			setTitle(myAgent.getLocalName());

		jTextArea = new JTextArea();
		jTextArea.setBackground(new Color(255, 255, 240));
		jTextArea.setEditable(false);
		jTextArea.setColumns(10);
		jTextArea.setRows(5);
		JScrollPane jScrollPane = new JScrollPane(jTextArea);
		getContentPane().add(BorderLayout.CENTER, jScrollPane);

		JPanel p = new JPanel();
		p.setLayout(new GridLayout(0, 4, 0, 0));

		JButton addButton = new JButton("Start Enchere Hollandaise");
		addButton.addActionListener(event -> {
			try {
				// SEND A GUI EVENT TO THE AGENT !!!
				GuiEvent guiEv = new GuiEvent(this, AlertAgent.ALERT);
				myAgent.postGuiEvent(guiEv);
				// END SEND A GUI EVENT TO THE AGENT !!!
			} catch (Exception e) {
				JOptionPane.showMessageDialog(EnchereGui.this, "Invalid values. " + e.getMessage(), "Error",
						JOptionPane.ERROR_MESSAGE);
			}
		});
		p.add(addButton);
		getContentPane().add(p, BorderLayout.SOUTH);


		// Make the agent terminate when the user closes
		// the GUI using the button on the upper right corner
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				// SEND AN GUI EVENT TO THE AGENT !!!
				GuiEvent guiEv = new GuiEvent(this, EnchereAgent.EXIT);
				myAgent.postGuiEvent(guiEv);
				// END SEND AN GUI EVENT TO THE AGENT !!!
			}
		});

		setResizable(true);
	}


	/** add a string to the text area */
	public void println(String chaine) {
		String texte = jTextArea.getText();
		texte = texte + chaine + "\n";
		jTextArea.setText(texte);
		jTextArea.setCaretPosition(texte.length());
	}

	public void setColor(Color color) {
		jTextArea.setBackground(color);
	}


	public static void main(String[] args) {
		EnchereGui test = new EnchereGui(null);
		test.setVisible(true);
	}

}
