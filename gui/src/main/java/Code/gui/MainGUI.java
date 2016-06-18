package Code.gui;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

public class MainGUI extends JFrame {

	private static final long serialVersionUID = -4318422896543101320L;
	
	private JPanel contentPane;
	private JTextField fileInputField;
	private JLabel leftImageLabel;

	/**
	 * @param image Imported Image
	 */
	public void setLeftImage(BufferedImage image) {
		leftImageLabel.setIcon(new ImageIcon(image));
		leftImageLabel.setText("");
	}

	/**
	 * @param image Exported Image
	 */
	public void setRightImage(BufferedImage image) {
		rightImageLabel.setIcon(new ImageIcon(image));
		rightImageLabel.setText("");
	}
	
	

	private JLabel rightImageLabel;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					MainGUI frame = new MainGUI();
					frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the frame.
	 */
	public MainGUI() {
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 450, 300);
		
		JMenuBar menuBar = new JMenuBar();
		setJMenuBar(menuBar);
		
		JMenu mnFile = new JMenu("File");
		menuBar.add(mnFile);
		
		JMenuItem mntmLoadProcessedImage = new JMenuItem("Load Processed Image");
		mnFile.add(mntmLoadProcessedImage);
		
		JSeparator separator = new JSeparator();
		mnFile.add(separator);
		
		JMenuItem mntmQuit = new JMenuItem("Quit");
		mnFile.add(mntmQuit);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		
		fileInputField = new JTextField();
		fileInputField.setColumns(10);
		
		JSplitPane splitPane = new JSplitPane();
		SpringLayout sl_contentPane = new SpringLayout();
		sl_contentPane.putConstraint(SpringLayout.WEST, splitPane, 5, SpringLayout.EAST, fileInputField);
		sl_contentPane.putConstraint(SpringLayout.EAST, splitPane, -5, SpringLayout.EAST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, fileInputField, 200, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.WEST, fileInputField, 5, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.NORTH, fileInputField, 6, SpringLayout.NORTH, contentPane);
		contentPane.setLayout(sl_contentPane);
		contentPane.add(fileInputField);
		contentPane.add(splitPane);
		
		leftImageLabel = new JLabel("No Image Loaded");
		leftImageLabel.setHorizontalAlignment(SwingConstants.LEFT);
		sl_contentPane.putConstraint(SpringLayout.NORTH, leftImageLabel, 26, SpringLayout.NORTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, leftImageLabel, -5, SpringLayout.SOUTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.WEST, leftImageLabel, 10, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, leftImageLabel, -210, SpringLayout.EAST, contentPane);
		contentPane.add(leftImageLabel);
		
		rightImageLabel = new JLabel("Image has not been processed yet.");
		sl_contentPane.putConstraint(SpringLayout.NORTH, rightImageLabel, 33, SpringLayout.NORTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, rightImageLabel, -5, SpringLayout.SOUTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, splitPane, -3, SpringLayout.NORTH, rightImageLabel);
		
		JButton btnLoadImage = new JButton("Load Image");
		btnLoadImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//TODO Choose file and add to fileInputField
			}
		});
		splitPane.setLeftComponent(btnLoadImage);
		
		JButton btnProcessImage = new JButton("Process Image");
		btnProcessImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				//TODO Start processing image and show in rightImageLabel's icon.
			}
		});
		splitPane.setRightComponent(btnProcessImage);
		rightImageLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		sl_contentPane.putConstraint(SpringLayout.WEST, rightImageLabel, 10, SpringLayout.EAST, leftImageLabel);
		sl_contentPane.putConstraint(SpringLayout.EAST, rightImageLabel, -10, SpringLayout.EAST, contentPane);
		contentPane.add(rightImageLabel);
	}
}
