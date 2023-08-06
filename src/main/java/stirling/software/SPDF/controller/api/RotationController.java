package stirling.software.SPDF.controller.api;

import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import stirling.software.SPDF.utils.WebResponseUtils;

@RestController
@Tag(name = "General", description = "General APIs")
public class RotationController {

    private static final Logger logger = LoggerFactory.getLogger(RotationController.class);

    @PostMapping(consumes = "multipart/form-data", value = "/rotate-pdf")
    @Operation(
        summary = "Rotate a PDF file",
        description = "This endpoint rotates a given PDF file by a specified angle. The angle must be a multiple of 90. Input:PDF Output:PDF Type:SISO"
    )
    public ResponseEntity<byte[]> rotatePDF(
        @RequestPart(required = true, value = "fileInput")
        @Parameter(description = "The PDF file to be rotated", required = true)
            MultipartFile pdfFile,
        @RequestParam("angle")
        @Parameter(description = "The angle by which to rotate the PDF file. This should be a multiple of 90.", example = "90", required = true)
            Integer angle) throws IOException {
    	
        // Load the PDF document
        PDDocument document = PDDocument.load(pdfFile.getBytes());

        // Get the list of pages in the document
        PDPageTree pages = document.getPages();

        for (PDPage page : pages) {
            page.setRotation(page.getRotation() + angle);
        }

        return WebResponseUtils.pdfDocToWebResponse(document, pdfFile.getOriginalFilename().replaceFirst("[.][^.]+$", "") + "_rotated.pdf");

    }

}
