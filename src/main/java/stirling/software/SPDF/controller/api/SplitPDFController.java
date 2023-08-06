package stirling.software.SPDF.controller.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
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
import stirling.software.SPDF.utils.GeneralUtils;
import stirling.software.SPDF.utils.WebResponseUtils;

@RestController
@Tag(name = "General", description = "General APIs")
public class SplitPDFController {

    private static final Logger logger = LoggerFactory.getLogger(SplitPDFController.class);

    @PostMapping(consumes = "multipart/form-data", value = "/split-pages")
    @Operation(summary = "Split a PDF file into separate documents",
            description = "This endpoint splits a given PDF file into separate documents based on the specified page numbers or ranges. Users can specify pages using individual numbers, ranges, or 'all' for every page. Input:PDF Output:PDF Type:SIMO")
    public ResponseEntity<byte[]> splitPdf(
            @RequestPart(required = true, value = "fileInput")
            @Parameter(description = "The input PDF file to be split")
                    MultipartFile file,
            @RequestParam("pages")
            @Parameter(description = "The pages to be included in separate documents. Specify individual page numbers (e.g., '1,3,5'), ranges (e.g., '1-3,5-7'), or 'all' for every page.")
                    String pages) throws IOException {
        // parse user input

        // open the pdf document
        InputStream inputStream = file.getInputStream();
        PDDocument document = PDDocument.load(inputStream);

        List<Integer> pageNumbers = new ArrayList<>();
        pages = pages.replaceAll("\\s+", ""); // remove whitespaces
        if (pages.toLowerCase().equals("all")) {
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                pageNumbers.add(i);
            }
        } else {
            String[] splitPoints = pages.split(",");
            for (String splitPoint : splitPoints) {
                List<Integer> orderedPages = GeneralUtils.parsePageList(new String[] {splitPoint}, document.getNumberOfPages());
                pageNumbers.addAll(orderedPages);
            }
            // Add the last page as a split point
            pageNumbers.add(document.getNumberOfPages() - 1);
        }

        logger.info("Splitting PDF into pages: {}", pageNumbers.stream().map(String::valueOf).collect(Collectors.joining(",")));

        // split the document
        List<ByteArrayOutputStream> splitDocumentsBoas = new ArrayList<>();
        int previousPageNumber = 0;
        for (int splitPoint : pageNumbers) {
            try (PDDocument splitDocument = new PDDocument()) {
                for (int i = previousPageNumber; i <= splitPoint; i++) {
                    PDPage page = document.getPage(i);
                    splitDocument.addPage(page);
                    logger.debug("Adding page {} to split document", i);
                }
                previousPageNumber = splitPoint + 1;

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                splitDocument.save(baos);

                splitDocumentsBoas.add(baos);
            } catch (Exception e) {
                logger.error("Failed splitting documents and saving them", e);
                throw e;
            }
        }


        // closing the original document
        document.close();

        Path zipFile = Files.createTempFile("split_documents", ".zip");

        String filename = file.getOriginalFilename().replaceFirst("[.][^.]+$", "");
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            // loop through the split documents and write them to the zip file
            for (int i = 0; i < splitDocumentsBoas.size(); i++) {
                String fileName = filename + "_" + (i + 1) + ".pdf";
                ByteArrayOutputStream baos = splitDocumentsBoas.get(i);
                byte[] pdf = baos.toByteArray();

                // Add PDF file to the zip
                ZipEntry pdfEntry = new ZipEntry(fileName);
                zipOut.putNextEntry(pdfEntry);
                zipOut.write(pdf);
                zipOut.closeEntry();

                logger.info("Wrote split document {} to zip file", fileName);
            }
        } catch (Exception e) {
            logger.error("Failed writing to zip", e);
            throw e;
        }

        logger.info("Successfully created zip file with split documents: {}", zipFile.toString());
        byte[] data = Files.readAllBytes(zipFile);
        Files.delete(zipFile);

        // return the Resource in the response
        return WebResponseUtils.bytesToWebResponse(data, filename + ".zip", MediaType.APPLICATION_OCTET_STREAM);
        
    }

}
