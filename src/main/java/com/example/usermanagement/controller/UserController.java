package com.example.usermanagement.controller;

import java.io.IOException;
import java.util.List;

import com.example.usermanagement.entity.Department;
import com.example.usermanagement.entity.Role;
import com.example.usermanagement.entity.User;
import com.example.usermanagement.service.FileStorageService;
import com.example.usermanagement.service.UserService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final FileStorageService fileStorageService;

    public UserController(UserService userService, FileStorageService fileStorageService) {
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    @ModelAttribute("departments")
    public List<Department> departments() {
        return userService.findAllDepartments();
    }

    @ModelAttribute("availableRoles")
    public List<Role> roles() {
        return userService.findAllRoles();
    }

    @GetMapping("/list")
    public String list(@RequestParam(required = false) String keyword, Model model) {
        List<User> users = userService.findUsers(keyword);
        model.addAttribute("users", users);
        model.addAttribute("userCount", users.size());
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        return "users/list";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        model.addAttribute("user", new User());
        return "users/add";
    }

    @PostMapping("/add")
    public String add(@Valid @ModelAttribute User user,
                      BindingResult result,
                      @RequestParam(required = false) MultipartFile photo,
                      Model model,
                      RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            return "users/add";
        }

        String storedImage = null;
        try {
            if (photo != null && !photo.isEmpty()) {
                storedImage = fileStorageService.storeImage(photo);
                user.setPhotoPath(storedImage);
            }
            userService.saveUser(user);
        } catch (IOException exception) {
            model.addAttribute("error", exception.getMessage());
            return "users/add";
        } catch (DataIntegrityViolationException exception) {
            fileStorageService.delete(storedImage);
            model.addAttribute("error", "That username is already in use.");
            return "users/add";
        }

        redirectAttributes.addFlashAttribute("success", "User added successfully.");
        return "redirect:/users/list";
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable int id, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getUserById(id);
        if (user == null) {
            return redirectWithMissingUser(redirectAttributes);
        }
        model.addAttribute("user", user);
        return "users/edit";
    }

    @PostMapping("/edit")
    public String edit(@Valid @ModelAttribute User user,
                       BindingResult result,
                       @RequestParam(required = false) MultipartFile photo,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        User existing = userService.getUserById(user.getId());
        if (existing == null) {
            return redirectWithMissingUser(redirectAttributes);
        }
        user.setPhotoPath(existing.getPhotoPath());

        if (result.hasErrors()) {
            return "users/edit";
        }

        String newImage = null;
        try {
            if (photo != null && !photo.isEmpty()) {
                newImage = fileStorageService.storeImage(photo);
                user.setPhotoPath(newImage);
            }
            userService.saveUser(user);
        } catch (IOException exception) {
            model.addAttribute("error", exception.getMessage());
            return "users/edit";
        } catch (DataIntegrityViolationException exception) {
            fileStorageService.delete(newImage);
            user.setPhotoPath(existing.getPhotoPath());
            model.addAttribute("error", "That username is already in use.");
            return "users/edit";
        }

        if (newImage != null) {
            fileStorageService.delete(existing.getPhotoPath());
        }
        redirectAttributes.addFlashAttribute("success", "User updated successfully.");
        return "redirect:/users/list";
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable int id, Model model, RedirectAttributes redirectAttributes) {
        User user = userService.getUserById(id);
        if (user == null) {
            return redirectWithMissingUser(redirectAttributes);
        }
        model.addAttribute("user", user);
        return "users/detail";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable int id, RedirectAttributes redirectAttributes) {
        User user = userService.getUserById(id);
        if (user == null) {
            return redirectWithMissingUser(redirectAttributes);
        }

        userService.deleteUser(id);
        fileStorageService.delete(user.getPhotoPath());
        redirectAttributes.addFlashAttribute("success", "User deleted successfully.");
        return "redirect:/users/list";
    }

    private String redirectWithMissingUser(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("error", "User not found.");
        return "redirect:/users/list";
    }
}
