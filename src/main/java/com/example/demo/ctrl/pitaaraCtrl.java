package com.example.demo.ctrl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.demo.bean.Artist;
import com.example.demo.bean.User;
import com.example.demo.dao.ArtistRepository;
import com.example.demo.dao.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

@Controller
public class pitaaraCtrl {

    @Autowired
    private ArtistRepository aR;

    @Autowired
    private UserRepository uR;

    private Cloudinary cloudinary;

    public pitaaraCtrl(@Value("${cloudinary.cloud_name}") String cloudName,
                       @Value("${cloudinary.api_key}") String apiKey,
                       @Value("${cloudinary.api_secret}") String apiSecret) {
        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret
        ));
    }

    // ======== INDEX ========
    @RequestMapping("/index")
    public String index(HttpSession session) {
        List<Artist> list = (List<Artist>) aR.findAll();
        session.setAttribute("show1", list);
        return "index";
    }

    // ======== ADMIN DASHBOARD ========
    @RequestMapping("/admindashboard")
    public String admindashboard(HttpSession session) {
        List<Artist> list = (List<Artist>) aR.findAll();
        session.setAttribute("show", list);
        return "admindashboard";
    }

    // ======== USER REGISTRATION ========
    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String user_register(User u, HttpSession session) {
        if (u.getPassword().equals(u.getConfirmPassword())) {
            try {
                uR.save(u);
                session.setAttribute("register-success", "Registration Successfully!...");
            } catch (Exception e) {
                session.setAttribute("email-exist", "Email Already Exist!...");
            }
        } else {
            session.setAttribute("password-match", "Please Check password and confirm password!...");
        }
        return "register";
    }

    // ======== USER LOGIN ========
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @PostMapping("/login")
    public String user_login(User u, HttpSession session) {
        for (User u1 : uR.findAll()) {
            if (u1.getUserName().equals(u.getUserName()) && u1.getPassword().equals(u.getPassword())) {
                session.setAttribute("loginName", u1);
                return "index";
            }
        }
        session.setAttribute("Login-failed", "Login Failed!...");
        return "login";
    }

    // ======== ADD ARTIST ========
    @GetMapping("/addArtist")
    public String addArtist() {
        return "addArtist";
    }

    @PostMapping("/saveArtist")
    public String saveArtist(Artist a,
                             @RequestParam("profile") MultipartFile profileFile,
                             @RequestParam("profile1") MultipartFile[] moreFiles) throws IOException {

        // Upload profile photo
        a.setProfilePhoto(uploadToCloudinary(profileFile));

        // Upload gallery photos
        a.setMorePhoto(uploadMultipleToCloudinary(moreFiles));

        aR.save(a);
        return "addArtist";
    }

    // ======== UPDATE ARTIST ========
    @PostMapping("/updateArtist")
    public String updateArtist(
            Artist a,
            @RequestParam("profile") MultipartFile profileFile,
            @RequestParam("profile1") MultipartFile[] galleryFiles,
            @RequestParam("remainingPhotos") String remainingPhotos
    ) throws IOException {

        Optional<Artist> optional = aR.findById(a.getId());
        if (!optional.isPresent()) return "error";

        Artist existingArtist = optional.get();

        // Profile photo
        if (profileFile != null && !profileFile.isEmpty()) {
            a.setProfilePhoto(uploadToCloudinary(profileFile));
        } else {
            a.setProfilePhoto(existingArtist.getProfilePhoto());
        }

        // Gallery photos
        List<String> finalGalleryPhotos = new ArrayList<>();
        if (remainingPhotos != null && !remainingPhotos.isEmpty()) {
            finalGalleryPhotos.addAll(Arrays.asList(remainingPhotos.split(",")));
        }

        if (galleryFiles != null) {
            for (MultipartFile file : galleryFiles) {
                if (file != null && !file.isEmpty()) {
                    finalGalleryPhotos.add(uploadToCloudinary(file));
                }
            }
        }

        a.setMorePhoto(String.join(",", finalGalleryPhotos));

        aR.save(a);
        return "redirect:admindashboard";
    }

    // ======== HELPER METHODS ========
    private String uploadToCloudinary(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return null;
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.emptyMap());
        return uploadResult.get("secure_url").toString();
    }

    private String uploadMultipleToCloudinary(MultipartFile[] files) throws IOException {
        if (files == null || files.length == 0) return null;
        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : files) {
            String url = uploadToCloudinary(file);
            if (sb.length() > 0) sb.append(",");
            sb.append(url);
        }
        return sb.toString();
    }

    // ======== DELETE ARTIST ========
    @RequestMapping("/deleteArtist")
    public String deleteArtist(int id) {
        aR.findById(id).ifPresent(aR::delete);
        return "admindashboard";
    }

    // ======== SEARCH ARTIST ========
    @RequestMapping("/searchbar")
    public String searchbar(Artist a, HttpSession session) {
        List<Artist> allArtists = (List<Artist>) aR.findAll();
        List<Artist> filteredArtists = new ArrayList<>();

        for (Artist artist : allArtists) {
            boolean matches = true;

            if (a.getLocation() != null && !a.getLocation().equalsIgnoreCase(artist.getLocation()))
                matches = false;
            if (a.getArtistType() != null && !a.getArtistType().equalsIgnoreCase(artist.getArtistType()))
                matches = false;
            if (a.getGender() != null && !a.getGender().equalsIgnoreCase(artist.getGender()))
                matches = false;
            if (a.getFullName() != null && !a.getFullName().equalsIgnoreCase(artist.getFullName()))
                matches = false;

            if (a.getLanguages() != null) {
                String[] userLangs = a.getLanguages().split(",");
                String[] artistLangs = artist.getLanguages() != null ? artist.getLanguages().split(",") : new String[0];
                boolean langMatch = Arrays.stream(userLangs)
                        .anyMatch(lang -> Arrays.stream(artistLangs).anyMatch(al -> al.trim().equalsIgnoreCase(lang.trim())));
                if (!langMatch) matches = false;
            }

            if (matches) filteredArtists.add(artist);
        }

        session.setAttribute("searchlist", filteredArtists);
        session.setAttribute("no", filteredArtists.size());
        return "index";
    }

    // ======== SINGLE PROFILE VIEW ========
    @RequestMapping("/singleProfile")
    public String singleProfile(Artist a1, HttpSession session) {
        aR.findById(a1.getId()).ifPresent(a -> session.setAttribute("singleProfile", a));
        return "index";
    }

    // ======== PREMIUM MEMBERS ========
    @GetMapping("/premiumMember")
    public String premiumMember(HttpSession session) {
        List<Artist> list = new ArrayList<>();
        for (Artist a : aR.findAll()) {
            if ("Yes".equalsIgnoreCase(a.getPermiumMember())) list.add(a);
        }
        session.setAttribute("searchlist", list);
        session.setAttribute("no", list.size());
        return "redirect:index";
    }

    // ======== PDF GENERATION ========
    @GetMapping("/generate-pdf")
    public ResponseEntity<byte[]> generatePdf(Artist a) throws IOException {
        Optional<Artist> op = aR.findById(a.getId());
        if (!op.isPresent()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        Artist artist = op.get();
        StringBuilder morePhotosHtml = new StringBuilder("<div class='photo-grid'>");
        if (artist.getMorePhoto() != null) {
            for (String url : artist.getMorePhoto().split(",")) {
                if (!url.trim().isEmpty()) {
                    morePhotosHtml.append("<img class='grid-image' src=\"").append(url).append("\" />");
                }
            }
        }
        morePhotosHtml.append("</div>");

        String html = "<html><head><style>" +
                ".photo-grid { display: flex; flex-wrap: wrap; gap: 10px; }" +
                ".grid-image { width: 100px; height: 100px; object-fit: cover; border: 1px solid #ccc; border-radius: 4px; padding: 4px; }" +
                "img.profile { width: 100px; border-radius: 4px; }" +
                "</style></head><body>" +
                "<h2>Artist Profile</h2>" +
                "<table>" +
                "<tr><th>Full Name</th><td>" + artist.getFullName() + "</td></tr>" +
                "<tr><th>Artist Type</th><td>" + artist.getArtistType() + "</td></tr>" +
                "<tr><th>Profile Photo</th><td><img class='profile' src='" + artist.getProfilePhoto() + "' /></td></tr>" +
                "<tr><th>More Photos</th><td>" + morePhotosHtml + "</td></tr>" +
                "</table>" +
                "</body></html>";

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();

            byte[] pdfBytes = os.toByteArray();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=artist_" + artist.getFullName() + ".pdf")
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}
