package inventory;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

public class InventoryManagerGUI extends JFrame {
    private InventoryManager inventoryManager;
    private JTable inventoryTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    private JLabel statusLabel;
    private JLabel totalValueLabel;
    
    public InventoryManagerGUI() {
        inventoryManager = new InventoryManager();
        initializeGUI();
        refreshTable();
        checkLowStock();
    }
    
    private void initializeGUI() {
        setTitle("Inventory Manager System");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        
        createMenuBar();
        
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        JPanel centerPanel = createCenterPanel();
        add(centerPanel, BorderLayout.CENTER);
        
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
        
        setLocationRelativeTo(null);
    }
    
    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        
        JMenu fileMenu = new JMenu("File");
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(exitItem);
        
        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About");
        aboutItem.addActionListener(e -> showAboutDialog());
        helpMenu.add(aboutItem);
        
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        panel.add(new JLabel("Search:"));
        searchField = new JTextField(20);
        panel.add(searchField);
        
        JButton searchButton = new JButton("Search");
        searchButton.addActionListener(e -> searchProducts());
        panel.add(searchButton);
        
        JButton showAllButton = new JButton("Show All");
        showAllButton.addActionListener(e -> refreshTable());
        panel.add(showAllButton);
        
        JComboBox<String> categoryFilter = new JComboBox<>(new String[]{"All", "Electronics", "Grocery", "Clothing"});
        categoryFilter.addActionListener(e -> filterByCategory((String) categoryFilter.getSelectedItem()));
        panel.add(new JLabel("Category:"));
        panel.add(categoryFilter);
        
        return panel;
    }
    
    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));
        
        String[] columnNames = {"ID", "Name", "Price", "Quantity", "Category", "Details"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        inventoryTable = new JTable(tableModel);
        inventoryTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        inventoryTable.setRowHeight(25);
        
        JScrollPane scrollPane = new JScrollPane(inventoryTable);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        
        JButton addButton = new JButton("Add Product");
        addButton.addActionListener(e -> showAddProductDialog());
        buttonPanel.add(addButton);
        
        JButton editButton = new JButton("Edit Product");
        editButton.addActionListener(e -> showEditProductDialog());
        buttonPanel.add(editButton);
        
        JButton deleteButton = new JButton("Delete Product");
        deleteButton.addActionListener(e -> deleteSelectedProduct());
        buttonPanel.add(deleteButton);
        
        JButton lowStockButton = new JButton("Show Low Stock");
        lowStockButton.addActionListener(e -> showLowStockProducts());
        buttonPanel.add(lowStockButton);
        
        panel.add(buttonPanel, BorderLayout.CENTER);
        
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusLabel = new JLabel("Ready");
        totalValueLabel = new JLabel();
        statusPanel.add(statusLabel);
        statusPanel.add(new JLabel(" | "));
        statusPanel.add(totalValueLabel);
        panel.add(statusPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private void refreshTable() {
        tableModel.setRowCount(0);
        ArrayList<Product> products = inventoryManager.getAllProducts();
        for (Product product : products) {
            addProductToTable(product);
        }
        updateStatusBar();
    }
    
    private void addProductToTable(Product product) {
        Object[] row = {
            product.getProductId(),
            product.getProductName(),
            String.format("$%.2f", product.getPrice()),
            product.getQuantity(),
            product.getCategory(),
            getProductSpecificInfo(product)
        };
        tableModel.addRow(row);
    }
    
    private String getProductSpecificInfo(Product product) {
        if (product instanceof Electronics) {
            return ((Electronics) product).getWarrantyMonths() + " mo warranty";
        } else if (product instanceof Grocery) {
            return "Exp: " + ((Grocery) product).getExpirationDate();
        } else if (product instanceof Clothing) {
            return "Size: " + ((Clothing) product).getSize();
        }
        return "";
    }
    
    private void searchProducts() {
        String searchTerm = searchField.getText().trim();
        if (searchTerm.isEmpty()) {
            refreshTable();
            return;
        }
        
        tableModel.setRowCount(0);
        ArrayList<Product> results = inventoryManager.searchByName(searchTerm);
        for (Product product : results) {
            addProductToTable(product);
        }
        statusLabel.setText("Found " + results.size() + " product(s)");
    }
    
    private void filterByCategory(String category) {
        if (category.equals("All")) {
            refreshTable();
            return;
        }
        
        tableModel.setRowCount(0);
        ArrayList<Product> products = inventoryManager.getProductsByCategory(category);
        for (Product product : products) {
            addProductToTable(product);
        }
        updateStatusBar();
    }
    
    private void showAddProductDialog() {
        JDialog dialog = new JDialog(this, "Add New Product", true);
        dialog.setLayout(new GridLayout(8, 2, 10, 10));
        dialog.setSize(400, 350);
        
        JTextField idField = new JTextField();
        JTextField nameField = new JTextField();
        JTextField priceField = new JTextField();
        JTextField quantityField = new JTextField();
        JComboBox<String> categoryBox = new JComboBox<>(new String[]{"Electronics", "Grocery", "Clothing"});
        JTextField extraField = new JTextField();
        JLabel extraLabel = new JLabel("Warranty (months):");
        
        categoryBox.addActionListener(e -> {
            String selected = (String) categoryBox.getSelectedItem();
            if (selected.equals("Electronics")) {
                extraLabel.setText("Warranty (months):");
            } else if (selected.equals("Grocery")) {
                extraLabel.setText("Expiration Date:");
            } else if (selected.equals("Clothing")) {
                extraLabel.setText("Size:");
            }
        });
        
        dialog.add(new JLabel("Product ID:"));
        dialog.add(idField);
        dialog.add(new JLabel("Product Name:"));
        dialog.add(nameField);
        dialog.add(new JLabel("Price:"));
        dialog.add(priceField);
        dialog.add(new JLabel("Quantity:"));
        dialog.add(quantityField);
        dialog.add(new JLabel("Category:"));
        dialog.add(categoryBox);
        dialog.add(extraLabel);
        dialog.add(extraField);
        
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        
        saveButton.addActionListener(e -> {
            try {
                String id = idField.getText().trim();
                String name = nameField.getText().trim();
                double price = Double.parseDouble(priceField.getText().trim());
                int quantity = Integer.parseInt(quantityField.getText().trim());
                String category = (String) categoryBox.getSelectedItem();
                String extra = extraField.getText().trim();
                
                Product newProduct = null;
                if (category.equals("Electronics")) {
                    int warranty = Integer.parseInt(extra);
                    newProduct = new Electronics(id, name, price, quantity, warranty);
                } else if (category.equals("Grocery")) {
                    newProduct = new Grocery(id, name, price, quantity, extra);
                } else if (category.equals("Clothing")) {
                    newProduct = new Clothing(id, name, price, quantity, extra);
                }
                
                inventoryManager.addProduct(newProduct);
                refreshTable();
                checkLowStock();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Product added successfully!");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid number format!", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (InvalidProductException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        dialog.add(saveButton);
        dialog.add(cancelButton);
        
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    private void showEditProductDialog() {
        int selectedRow = inventoryTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product to edit.");
            return;
        }
        
        String productId = (String) tableModel.getValueAt(selectedRow, 0);
        Product product = inventoryManager.findProductById(productId);
        
        JDialog dialog = new JDialog(this, "Edit Product", true);
        dialog.setLayout(new GridLayout(4, 2, 10, 10));
        dialog.setSize(350, 200);
        
        JTextField priceField = new JTextField(String.valueOf(product.getPrice()));
        JTextField quantityField = new JTextField(String.valueOf(product.getQuantity()));
        
        dialog.add(new JLabel("Product: " + product.getProductName()));
        dialog.add(new JLabel(""));
        dialog.add(new JLabel("New Price:"));
        dialog.add(priceField);
        dialog.add(new JLabel("New Quantity:"));
        dialog.add(quantityField);
        
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        
        saveButton.addActionListener(e -> {
            try {
                double newPrice = Double.parseDouble(priceField.getText().trim());
                int newQuantity = Integer.parseInt(quantityField.getText().trim());
                
                inventoryManager.updatePrice(productId, newPrice);
                inventoryManager.updateQuantity(productId, newQuantity);
                refreshTable();
                checkLowStock();
                dialog.dispose();
                JOptionPane.showMessageDialog(this, "Product updated successfully!");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(dialog, "Invalid number format!", "Error", JOptionPane.ERROR_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> dialog.dispose());
        
        dialog.add(saveButton);
        dialog.add(cancelButton);
        
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    private void deleteSelectedProduct() {
        int selectedRow = inventoryTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product to delete.");
            return;
        }
        
        String productId = (String) tableModel.getValueAt(selectedRow, 0);
        String productName = (String) tableModel.getValueAt(selectedRow, 1);
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete " + productName + "?", 
            "Confirm Delete", 
            JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            inventoryManager.removeProduct(productId);
            refreshTable();
            JOptionPane.showMessageDialog(this, "Product deleted successfully!");
        }
    }
    
    private void showLowStockProducts() {
        ArrayList<Product> lowStock = inventoryManager.getLowStockProducts();
        if (lowStock.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No products with low stock!");
            return;
        }
        
        tableModel.setRowCount(0);
        for (Product product : lowStock) {
            addProductToTable(product);
        }
        statusLabel.setText("Showing " + lowStock.size() + " low stock product(s)");
    }
    
    private void checkLowStock() {
        ArrayList<Product> lowStock = inventoryManager.getLowStockProducts();
        if (!lowStock.isEmpty()) {
            StringBuilder message = new StringBuilder("Low Stock Alert!\n\n");
            for (Product product : lowStock) {
                message.append(product.getProductName())
                       .append(" - Quantity: ")
                       .append(product.getQuantity())
                       .append("\n");
            }
            JOptionPane.showMessageDialog(this, message.toString(), "Low Stock Warning", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    private void updateStatusBar() {
        int totalProducts = inventoryManager.getInventorySize();
        double totalValue = inventoryManager.calculateTotalValue();
        statusLabel.setText("Total Products: " + totalProducts);
        totalValueLabel.setText(String.format("Total Inventory Value: $%.2f", totalValue));
    }
    
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this, 
            "Inventory Manager v1.0\n\n" +
            "Developed by:\n" +
            "- Arhan Supramanyam\n" +
            "- Taha Whyne\n" +
            "- Syed Jawad Gilani\n\n" +
            "CP213 Course Project",
            "About", 
            JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            InventoryManagerGUI gui = new InventoryManagerGUI();
            gui.setVisible(true);
        });
    }
}