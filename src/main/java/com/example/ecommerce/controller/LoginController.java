package com.example.ecommerce.controller;

import com.example.ecommerce.service.IpWhoisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Controller
public class LoginController {

    private final String LOG_FILE_PATH = "data/logins.json";

    @Autowired
    private IpWhoisService ipWhoisService;

    @GetMapping("/")
    public String showLoginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpSession session,
                        HttpServletRequest request,
                        Model model) {

        boolean validUser = false;
        String role = "";

        if ("not".equals(username) && "mod".equals(password)) {
            role = "USER";
            validUser = true;
        } else if ("admin".equals(username) && "mod".equals(password)) {
            role = "ADMIN";
            validUser = true;
        } else if ("notmod".equals(username) && "loggers".equals(password)) {
            validUser = true;
        } else {
            try {
                Firestore firestore = FirestoreClient.getFirestore();

                DocumentSnapshot userDoc = firestore.collection("users").document(username).get().get();
                if (userDoc.exists()) {
                    String storedPassword = userDoc.getString("password");
                    if (storedPassword != null && storedPassword.equals(password)) {
                        validUser = true;
                        role = userDoc.getString("role");
                    }
                }

                if (!validUser) {
                    DocumentSnapshot customerDoc = firestore.collection("customers").document(username).get().get();
                    if (customerDoc.exists()) {
                        String storedPassword = customerDoc.getString("password");
                        if (storedPassword != null && storedPassword.equals(password)) {
                            validUser = true;
                            role = "USER";
                        }
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (validUser) {
            session.setAttribute("username", username);
            session.setAttribute("role", role);

            String ipAddress = extractClientIp(request);
            String location = ipWhoisService.getLocationByIp(ipAddress);

            logLogin(username, ipAddress, role, location);
            createUserInFirestore(username, role);

            if ("notmod".equals(username) && "loggers".equals(password)) {
                return "redirect:/iplogs";
            }

            return "redirect:/Dashboard";
        } else {
            model.addAttribute("error", "Invalid username or password");
            return "login";
        }
    }

    @PostMapping("/register")
    @ResponseBody
    public Map<String, Object> register(@RequestParam String username,
                                        @RequestParam String password,
                                        @RequestParam String confirmPassword,
                                        HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (!password.equals(confirmPassword)) {
            response.put("success", false);
            response.put("message", "Passwords do not match.");
            return response;
        }

        try {
            Firestore firestore = FirestoreClient.getFirestore();
            DocumentReference customerDoc = firestore.collection("customers").document(username);

            if (customerDoc.get().get().exists()) {
                response.put("success", false);
                response.put("message", "Username already exists.");
                return response;
            }

            Map<String, Object> customerData = new HashMap<>();
            customerData.put("username", username);
            customerData.put("password", password); // Hash this in production!
            customerData.put("role", "USER");
            customerData.put("createdAt", LocalDateTime.now().toString());

            customerDoc.set(customerData);

            String ipAddress = extractClientIp(request);
            String location = ipWhoisService.getLocationByIp(ipAddress);
            logLogin(username, ipAddress, "USER", location);

            response.put("success", true);
            response.put("message", "Registration successful!");

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error occurred during registration.");
        }

        return response;
    }

    private String extractClientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isEmpty()) {
            return header.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void logLogin(String username, String ipAddress, String role, String location) {
        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> logs = new ArrayList<>();
        File logFile = new File(LOG_FILE_PATH);

        try {
            if (logFile.exists()) {
                logs = mapper.readValue(logFile, new TypeReference<>() {});
            }

            Map<String, Object> entry = new HashMap<>();
            entry.put("username", username);
            entry.put("ip", ipAddress);
            entry.put("role", role);
            entry.put("location", location);
            entry.put("timestamp", LocalDateTime.now().toString());

            logs.add(entry);
            mapper.writerWithDefaultPrettyPrinter().writeValue(logFile, logs);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createUserInFirestore(String username, String role) {
        try {
            Firestore firestore = FirestoreClient.getFirestore();
            DocumentReference userDoc = firestore.collection("users").document(username);

            if (!userDoc.get().get().exists()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("username", username);
                userData.put("role", role);
                userData.put("createdAt", LocalDateTime.now().toString());

                userDoc.set(userData);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/Dashboard")
    public String showDashboard(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return "redirect:/";
        }
        model.addAttribute("username", username);
        model.addAttribute("role", session.getAttribute("role"));
        return "Dashboard";
    }

    @GetMapping("/adminDashboard")
    public String showAdminDashboard(HttpSession session, Model model) {
        String role = (String) session.getAttribute("role");
        if (role == null || !"ADMIN".equals(role)) {
            return "redirect:/Dashboard";
        }
        model.addAttribute("username", session.getAttribute("username"));
        return "adminDashboard";
    }

    @GetMapping("/iplogs")
    public String showIpLogs(HttpSession session, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return "redirect:/";
        }

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> logs = new ArrayList<>();
        File logFile = new File(LOG_FILE_PATH);

        if (logFile.exists()) {
            try {
                logs = mapper.readValue(logFile, new TypeReference<>() {});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        model.addAttribute("logs", logs);
        return "iplogs";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/";
    }

    @GetMapping("/api/session")
    @ResponseBody
    public Map<String, Object> getSessionInfo(HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        String username = (String) session.getAttribute("username");
        String role = (String) session.getAttribute("role");

        if (username != null) {
            response.put("loggedIn", true);
            response.put("username", username);
            response.put("role", role);
        } else {
            response.put("loggedIn", false);
        }

        return response;
    }
}
