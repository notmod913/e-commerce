package com.example.ecommerce.controller;

import com.example.ecommerce.model.Product;
import com.example.ecommerce.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private ProductRepository productRepository;

    // Display Admin Dashboard
    @GetMapping
    public String showAdminDashboard(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");

        if (!"admin".equals(username)) {
            return "redirect:/";
        }

        long productCount = productRepository.count();
        List<Product> products = productRepository.findAll();

        model.addAttribute("username", username);
        model.addAttribute("productCount", productCount);
        model.addAttribute("products", products);
        return "adminDashboard";
    }

    // Handle Add, Update, and Delete in a Single Endpoint
    @PostMapping("/manageProduct")
    public String manageProduct(@RequestParam String actionType,
                                @RequestParam(required = false) String id,
                                @RequestParam(required = false) String name,
                                @RequestParam(required = false) Double price,
                                @RequestParam(required = false) String pictureUrl,
                                @RequestParam(required = false) String category,
                                Model model) {
        try {
            switch (actionType) {
                case "add":
                    if (name != null && price != null && pictureUrl != null && category != null) {
                        Product newProduct = new Product(name, price, pictureUrl, category);
                        productRepository.save(newProduct);
                        model.addAttribute("message", "Product added successfully.");
                    } else {
                        model.addAttribute("error", "All fields are required to add a product.");
                    }
                    break;

                case "update":
                    if (id == null) {
                        model.addAttribute("error", "Product ID is required for update.");
                        break;
                    }
                    Optional<Product> optionalProduct = productRepository.findById(id);
                    if (optionalProduct.isPresent()) {
                        Product productToUpdate = optionalProduct.get();

                        if (name != null) productToUpdate.setName(name);
                        if (price != null) productToUpdate.setPrice(price);
                        if (pictureUrl != null) productToUpdate.setPictureUrl(pictureUrl);
                        if (category != null) productToUpdate.setCategory(category);

                        productRepository.save(productToUpdate);
                        model.addAttribute("message", "Product updated successfully.");
                    } else {
                        model.addAttribute("error", "Product not found.");
                    }
                    break;

                case "delete":
                    if (id != null) {
                        productRepository.deleteById(id);
                        model.addAttribute("message", "Product deleted successfully.");
                    } else {
                        model.addAttribute("error", "Product ID is required for deletion.");
                    }
                    break;

                default:
                    model.addAttribute("error", "Invalid action type.");
            }
        } catch (Exception e) {
            model.addAttribute("error", "Error processing request: " + e.getMessage());
        }

        return "redirect:/admin";
    }
}
