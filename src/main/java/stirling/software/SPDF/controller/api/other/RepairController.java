package stirling.software.SPDF.controller.api.other;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
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
public class RepairController {

    private static final Logger logger = LoggerFactory.getLogger(RepairController.class);

    @PostMapping(consumes = "multipart/form-data", value = "/repair")
    @Operation(
        summary = "Repair a PDF file",
        description = "This endpoint repairs a given PDF file by running Ghostscript command. The PDF is first saved to a temporary location, repaired, read back, and then returned as a response. Input:PDF Output:PDF Type:SISO"
    )
    public ResponseEntity<byte[]> repairPdf(
        @RequestPart(required = true, value = "fileInput")
        @Parameter(description = "The input PDF file to be repaired", required = true)
            MultipartFile inputFile) throws IOException, InterruptedException {

        // Save the uploaded file to a temporary location
        Path tempInputFile = Files.createTempFile("input_", ".pdf");
        inputFile.transferTo(tempInputFile.toFile());

        // Prepare the output file path
        Path tempOutputFile = Files.createTempFile("output_", ".pdf");

        List<String> command = new ArrayList<>();
        command.add("gs");
        command.add("-o");
        command.add(tempOutputFile.toString());
        command.add("-sDEVICE=pdfwrite");
        command.add(tempInputFile.toString());


        ProcessExecutorResult returnCode = ProcessExecutor.getInstance(ProcessExecutor.Processes.GHOSTSCRIPT).runCommandWithOutputHandling(command);

        // Read the optimized PDF file
        byte[] pdfBytes = Files.readAllBytes(tempOutputFile);

        // Clean up the temporary files
        Files.delete(tempInputFile);
        Files.delete(tempOutputFile);

        // Return the optimized PDF as a response
        String outputFilename = inputFile.getOriginalFilename().replaceFirst("[.][^.]+$", "") + "_repaired.pdf";
        return WebResponseUtils.bytesToWebResponse(pdfBytes, outputFilename);
    }

}
