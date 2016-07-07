package io.github.dwarfbot.gui;

import io.github.dwarfbot.api.DecodedImage;
import io.github.dwarfbot.api.ImageReader;
import io.github.dwarfbot.api.Tileset;
import io.github.dwarfbot.api.TilesetFitter;
import io.github.dwarfbot.api.TilesetManager;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

public class MainGUI extends JFrame {

	private static final long serialVersionUID = -4318422896543101320L;
	
	private JPanel contentPane;
	private JTextField fileInputField;
	private JLabel leftImageLabel;
	private JComboBox<?> tilesetComboBox;

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
		TilesetManager bot = new TilesetManager();
		ArrayList<Tileset> tilesets = bot.getTilesets();
		
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
		SpringLayout sl_contentPane = new SpringLayout();
		sl_contentPane.putConstraint(SpringLayout.EAST, fileInputField, 209, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.WEST, fileInputField, 5, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.NORTH, fileInputField, 6, SpringLayout.NORTH, contentPane);
		contentPane.setLayout(sl_contentPane);
		contentPane.add(fileInputField);
		
		JButton btnLoadImage = new JButton("Load Image");
		sl_contentPane.putConstraint(SpringLayout.NORTH, btnLoadImage, -1, SpringLayout.NORTH, fileInputField);
		sl_contentPane.putConstraint(SpringLayout.WEST, btnLoadImage, 6, SpringLayout.EAST, fileInputField);
		contentPane.add(btnLoadImage);
		btnLoadImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JFileChooser chooser = new JFileChooser();
				FileNameExtensionFilter filter = new FileNameExtensionFilter("Supported image types", ImageIO.getReaderFormatNames());
				chooser.setFileFilter(filter);
				int returnCode = chooser.showOpenDialog(contentPane);
				if (returnCode == JFileChooser.APPROVE_OPTION) {
					fileInputField.setText(chooser.getSelectedFile().getAbsolutePath());
				}
			}
		});
		
		JButton btnProcessImage = new JButton("Process Image");
		sl_contentPane.putConstraint(SpringLayout.NORTH, btnProcessImage, -1, SpringLayout.NORTH, fileInputField);
		sl_contentPane.putConstraint(SpringLayout.WEST, btnProcessImage, 316, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, btnProcessImage, -5, SpringLayout.EAST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, btnLoadImage, -5, SpringLayout.WEST, btnProcessImage);
		contentPane.add(btnProcessImage);
		btnProcessImage.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				TilesetManager bot = new TilesetManager();
				ArrayList<Tileset> tilesets = bot.getTilesets();
				BufferedImage toConvert = ImageReader.loadImageFromDisk(fileInputField.getText());
				leftImageLabel.setText("");
				leftImageLabel.setIcon(new ImageIcon(toConvert));
				TilesetFitter fitter = new TilesetFitter(tilesets, false);
				fitter.loadImageForConverting(toConvert);
				DecodedImage decoded = fitter.decodeImage();
				fitter.exportRenderedImage(decoded, tilesetComboBox.getSelectedIndex(), "Exported/Converted.png");
				rightImageLabel.setText("");
				rightImageLabel.setIcon(new ImageIcon(ImageReader.loadImageFromDisk("Exported/Converted.png")));
			}
		});
		
		tilesetComboBox = new JComboBox(tilesets.toArray());
		sl_contentPane.putConstraint(SpringLayout.NORTH, tilesetComboBox, 5, SpringLayout.SOUTH, btnLoadImage);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, tilesetComboBox, 25, SpringLayout.SOUTH, btnLoadImage);
		sl_contentPane.putConstraint(SpringLayout.EAST, tilesetComboBox, 0, SpringLayout.EAST, contentPane);
		contentPane.add(tilesetComboBox);
		
		JLabel lblSelectTitleset = new JLabel("Select Titleset:");
		sl_contentPane.putConstraint(SpringLayout.NORTH, lblSelectTitleset, 10, SpringLayout.SOUTH, fileInputField);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, lblSelectTitleset, 0, SpringLayout.SOUTH, tilesetComboBox);
		sl_contentPane.putConstraint(SpringLayout.WEST, tilesetComboBox, 5, SpringLayout.EAST, lblSelectTitleset);
		sl_contentPane.putConstraint(SpringLayout.WEST, lblSelectTitleset, 0, SpringLayout.WEST, fileInputField);
		contentPane.add(lblSelectTitleset);
		
		JSplitPane splitPane = new JSplitPane();
		sl_contentPane.putConstraint(SpringLayout.NORTH, splitPane, 11, SpringLayout.SOUTH, tilesetComboBox);
		sl_contentPane.putConstraint(SpringLayout.WEST, splitPane, 0, SpringLayout.WEST, fileInputField);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, splitPane, 0, SpringLayout.SOUTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, splitPane, 0, SpringLayout.EAST, contentPane);
		contentPane.add(splitPane);
		
		leftImageLabel = new JLabel(" No Image Loaded ");
		splitPane.setLeftComponent(leftImageLabel);
		sl_contentPane.putConstraint(SpringLayout.NORTH, leftImageLabel, 56, SpringLayout.NORTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.WEST, leftImageLabel, 10, SpringLayout.WEST, contentPane);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, leftImageLabel, 0, SpringLayout.SOUTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, leftImageLabel, 210, SpringLayout.WEST, contentPane);
		leftImageLabel.setHorizontalAlignment(SwingConstants.LEFT);
		
		rightImageLabel = new JLabel(" Image has not been processed yet. ");
		splitPane.setRightComponent(rightImageLabel);
		sl_contentPane.putConstraint(SpringLayout.NORTH, rightImageLabel, 56, SpringLayout.NORTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.SOUTH, rightImageLabel, 0, SpringLayout.SOUTH, contentPane);
		sl_contentPane.putConstraint(SpringLayout.EAST, rightImageLabel, -10, SpringLayout.EAST, contentPane);
		rightImageLabel.setHorizontalAlignment(SwingConstants.RIGHT);
		sl_contentPane.putConstraint(SpringLayout.WEST, rightImageLabel, 60, SpringLayout.EAST, leftImageLabel);
	}
}
