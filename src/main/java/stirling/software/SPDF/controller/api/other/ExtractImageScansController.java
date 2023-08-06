package stirling.software.SPDF.controller.api.other;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import stirling.software.SPDF.utils.ProcessExecutor;
import stirling.software.SPDF.utils.ProcessExecutor.ProcessExecutorResult;
import stirling.software.SPDF.utils.WebResponseUtils;

@RestController
@Tag(name = "Other", description = "Other APIs")
public class ExtractImageScansController {

    private static final Logger logger = LoggerFactory.getLogger(ExtractImageScansController.class);

    @PostMapping(consumes = "multipart/form-data", value = "/extract-image-scans")
    @Operation(summary = "Extract image scans from an input file",
            description = "This endpoint extracts image scans from a given file based on certain parameters. Users can specify angle threshold, tolerance, minimum area, minimum contour area, and border size. Input:PDF Output:IMAGE/ZIP Type:SIMO")
    public ResponseEntity<byte[]> extractImageScans(
            @RequestPart(required = true, value = "fileInput")
            @Parameter(description = "The input file containing image scans")
                    MultipartFile inputFile,
            @RequestParam(name = "angle_threshold", defaultValue = "5")
            @Parameter(description = "The angle threshold for the image scan extraction", example = "5")
                    int angleThreshold,
            @RequestParam(name = "tolerance", defaultValue = "20")
            @Parameter(description = "The tolerance for the image scan extraction", example = "20")
                    int tolerance,
            @RequestParam(name = "min_area", defaultValue = "8000")
            @Parameter(description = "The minimum area for the image scan extraction", example = "8000")
                    int minArea,
            @RequestParam(name = "min_contour_area", defaultValue = "500")
            @Parameter(description = "The minimum contour area for the image scan extraction", example = "500")
                    int minContourArea,
            @RequestParam(name = "border_size", defaultValue = "1")
            @Parameter(description = "The border size for the image scan extraction", example = "1")
                    int borderSize) throws IOException, InterruptedException {

        String fileName = inputFile.getOriginalFilename();
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1);

        List<String> images = new ArrayList<>();

        // Check if input file is a PDF
        if (extension.equalsIgnoreCase("pdf")) {
            // Load PDF document
            try (PDDocument document = PDDocument.load(new ByteArrayInputStream(inputFile.getBytes()))) {
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                int pageCount = document.getNumberOfPages();
                images = new ArrayList<>();

                // Create images of all pages
                for (int i = 0; i < pageCount; i++) {
                    // Create temp file to save the image
                    Path tempFile = Files.createTempFile("image_", ".png");

                    // Render image and save as temp file
                    BufferedImage image = pdfRenderer.renderImageWithDPI(i, 300);
                    ImageIO.write(image, "png", tempFile.toFile());

                    // Add temp file path to images list
                    images.add(tempFile.toString());
                }
            }
        } else {
            Path tempInputFile = Files.createTempFile("input_", "." + extension);
            Files.copy(inputFile.getInputStream(), tempInputFile, StandardCopyOption.REPLACE_EXISTING);
            // Add input file path to images list
            images.add(tempInputFile.toString());
        }

        List<byte[]> processedImageBytes = new ArrayList<>();

        // Process each image
        for (int i = 0; i < images.size(); i++) {

            Path tempDir = Files.createTempDirectory("openCV_output");
            List<String> command = new ArrayList<>(Arrays.asList(
                    "python3", 
                    "./scripts/split_photos.py", 
                    images.get(i), 
                    tempDir.toString(), 
                    "--angle_threshold", String.valueOf(angleThreshold),
                    "--tolerance", String.valueOf(tolerance),
                    "--min_area", String.valueOf(minArea),
                    "--min_contour_area", String.valueOf(minContourArea),
                    "--border_size", String.valueOf(borderSize)
                ));


            // Run CLI command
            ProcessExecutorResult returnCode = ProcessExecutor.getInstance(ProcessExecutor.Processes.PYTHON_OPENCV).runCommandWithOutputHandling(command);

            // Read the output photos in temp directory
            List<Path> tempOutputFiles = Files.list(tempDir).sorted().collect(Collectors.toList());
            for (Path tempOutputFile : tempOutputFiles) {
                byte[] imageBytes = Files.readAllBytes(tempOutputFile);
                processedImageBytes.add(imageBytes);
            }
            // Clean up the temporary directory
            FileUtils.deleteDirectory(tempDir.toFile());
        }

        // Create zip file if multiple images
        if (processedImageBytes.size() > 1) {
            String outputZipFilename = fileName.replaceFirst("[.][^.]+$", "") + "_processed.zip";
            Path tempZipFile = Files.createTempFile("output_", ".zip");

            try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(tempZipFile.toFile()))) {
                // Add processed images to the zip
                for (int i = 0; i < processedImageBytes.size(); i++) {
                    ZipEntry entry = new ZipEntry(fileName.replaceFirst("[.][^.]+$", "") + "_" + (i + 1) + ".png");
                    zipOut.putNextEntry(entry);
                    zipOut.write(processedImageBytes.get(i));
                    zipOut.closeEntry();
                }
            }

            byte[] zipBytes = Files.readAllBytes(tempZipFile);

            // Clean up the temporary zip file
            Files.delete(tempZipFile);

            return WebResponseUtils.bytesToWebResponse(zipBytes, outputZipFilename, MediaType.APPLICATION_OCTET_STREAM);
        } else {
            // Return the processed image as a response
            byte[] imageBytes = processedImageBytes.get(0);
            return WebResponseUtils.bytesToWebResponse(imageBytes, fileName.replaceFirst("[.][^.]+$", "") + ".png", MediaType.IMAGE_PNG);
        }

    }

}
