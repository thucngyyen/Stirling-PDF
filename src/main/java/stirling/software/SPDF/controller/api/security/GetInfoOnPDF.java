package stirling.software.SPDF.controller.api.security;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.encryption.PDEncryption;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;

import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfOutline;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfCatalog;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfEncryption;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfResources;
import com.itextpdf.kernel.pdf.PdfStream;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfViewerPreferences;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfFileAttachmentAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfLinkAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.layer.PdfLayer;
import com.itextpdf.kernel.pdf.layer.PdfOCProperties;
import com.itextpdf.kernel.xmp.XMPException;
import com.itextpdf.kernel.xmp.XMPMeta;
import com.itextpdf.kernel.xmp.XMPMetaFactory;
import com.itextpdf.kernel.xmp.options.SerializeOptions;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import stirling.software.SPDF.utils.WebResponseUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
@RestController
@Tag(name = "Security", description = "Security APIs")
public class GetInfoOnPDF {
	
	static ObjectMapper objectMapper = new ObjectMapper();

	@PostMapping(consumes = "multipart/form-data", value = "/get-info-on-pdf")
    @Operation(summary = "Summary here", description = "desc. Input:PDF Output:JSON Type:SISO")
    public ResponseEntity<byte[]> getPdfInfo(
            @RequestPart(required = true, value = "fileInput") 
            @Parameter(description = "The input PDF file to get info on", required = true) MultipartFile inputFile)
            throws IOException {
		
		try (
			    PDDocument pdfBoxDoc = PDDocument.load(inputFile.getInputStream());
			    PdfDocument itextDoc = new PdfDocument(new PdfReader(inputFile.getInputStream()))
			) {
            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonOutput = objectMapper.createObjectNode();

            // Metadata using PDFBox
            PDDocumentInformation info = pdfBoxDoc.getDocumentInformation();
            ObjectNode metadata = objectMapper.createObjectNode();
            ObjectNode basicInfo = objectMapper.createObjectNode();
            ObjectNode docInfoNode = objectMapper.createObjectNode();
            ObjectNode compliancy = objectMapper.createObjectNode();
            ObjectNode encryption = objectMapper.createObjectNode();
            ObjectNode other = objectMapper.createObjectNode();
            
            
            metadata.put("Title", info.getTitle());
            metadata.put("Author", info.getAuthor());
            metadata.put("Subject", info.getSubject());
            metadata.put("Keywords", info.getKeywords());
            metadata.put("Producer", info.getProducer());
            metadata.put("Creator", info.getCreator());
            metadata.put("CreationDate", formatDate(info.getCreationDate()));
            metadata.put("ModificationDate", formatDate(info.getModificationDate()));
            jsonOutput.set("Metadata", metadata);
            
            
            
            
            // Total file size of the PDF
            long fileSizeInBytes = inputFile.getSize();
            basicInfo.put("FileSizeInBytes", fileSizeInBytes);
            
            // Number of words, paragraphs, and images in the entire document
            String fullText = new PDFTextStripper().getText(pdfBoxDoc);
            String[] words = fullText.split("\\s+");
            int wordCount = words.length;
            int paragraphCount = fullText.split("\r\n|\r|\n").length;
            basicInfo.put("WordCount", wordCount);
            basicInfo.put("ParagraphCount", paragraphCount);
            // Number of characters in the entire document (including spaces and special characters)
            int charCount = fullText.length();
            basicInfo.put("CharacterCount", charCount);
            
            
            // Initialize the flags and types
            boolean hasCompression = false;
            String compressionType = "None";

            // Check for object streams
            for (int i = 1; i <= itextDoc.getNumberOfPdfObjects(); i++) {
                PdfObject obj = itextDoc.getPdfObject(i);
                if (obj != null && obj.isStream() && ((PdfStream) obj).get(PdfName.Type) == PdfName.ObjStm) {
                    hasCompression = true;
                    compressionType = "Object Streams";
                    break;
                }
            }

            // If not compressed using object streams, check for compressed Xref tables
            if (!hasCompression && itextDoc.getReader().hasRebuiltXref()) {
                hasCompression = true;
                compressionType = "Compressed Xref or Rebuilt Xref";
            }
            basicInfo.put("Compression", hasCompression);
            if(hasCompression)
            	basicInfo.put("CompressionType", compressionType);
            
            String language = pdfBoxDoc.getDocumentCatalog().getLanguage();
            basicInfo.put("Language", language);
            basicInfo.put("Number of pages", pdfBoxDoc.getNumberOfPages());
            
            
            // Page Mode using iText7
            PdfCatalog catalog = itextDoc.getCatalog();
            PdfName pageMode = catalog.getPdfObject().getAsName(PdfName.PageMode);
            
            // Document Information using PDFBox
            docInfoNode.put("PDF version", pdfBoxDoc.getVersion());
            docInfoNode.put("Trapped", info.getTrapped());
            docInfoNode.put("Page Mode", getPageModeDescription(pageMode));;
            

            
            
            
            PdfAcroForm acroForm = PdfAcroForm.getAcroForm(itextDoc, false);
            ObjectNode formFieldsNode = objectMapper.createObjectNode();
            if (acroForm != null) {
                for (Map.Entry<String, PdfFormField> entry : acroForm.getFormFields().entrySet()) {
                    formFieldsNode.put(entry.getKey(), entry.getValue().getValueAsString());
                }
            }
            jsonOutput.set("FormFields", formFieldsNode);
           
            
            
            
            
            //embeed files TODO size
            ArrayNode embeddedFilesArray = objectMapper.createArrayNode();
            if(itextDoc.getCatalog().getPdfObject().getAsDictionary(PdfName.Names) != null)
            {
            PdfDictionary embeddedFiles = itextDoc.getCatalog().getPdfObject().getAsDictionary(PdfName.Names)
                    .getAsDictionary(PdfName.EmbeddedFiles);
            if (embeddedFiles != null) {
                
                PdfArray namesArray = embeddedFiles.getAsArray(PdfName.Names);
                if(namesArray != null) {
	                for (int i = 0; i < namesArray.size(); i += 2) {
	                    ObjectNode embeddedFileNode = objectMapper.createObjectNode();
	                    embeddedFileNode.put("Name", namesArray.getAsString(i).toString());
	                    // Add other details if required
	                    embeddedFilesArray.add(embeddedFileNode);
	                }
                }
                
            }
            }
            other.set("EmbeddedFiles", embeddedFilesArray);
            
            //attachments TODO size
            ArrayNode attachmentsArray = objectMapper.createArrayNode();
            for (int pageNum = 1; pageNum <= itextDoc.getNumberOfPages(); pageNum++) {
                for (PdfAnnotation annotation : itextDoc.getPage(pageNum).getAnnotations()) {
                    if (annotation instanceof PdfFileAttachmentAnnotation) {
                        ObjectNode attachmentNode = objectMapper.createObjectNode();
                        attachmentNode.put("Name", ((PdfFileAttachmentAnnotation) annotation).getName().toString());
                        attachmentNode.put("Description", annotation.getContents().getValue());
                        attachmentsArray.add(attachmentNode);
                    }
                }
            }
            other.set("Attachments", attachmentsArray);

            //Javascript
            PdfDictionary namesDict = itextDoc.getCatalog().getPdfObject().getAsDictionary(PdfName.Names);
            ArrayNode javascriptArray = objectMapper.createArrayNode();
            if (namesDict != null) {
                PdfDictionary javascriptDict = namesDict.getAsDictionary(PdfName.JavaScript);
                if (javascriptDict != null) {

                    PdfArray namesArray = javascriptDict.getAsArray(PdfName.Names);
                    for (int i = 0; i < namesArray.size(); i += 2) {
                        ObjectNode jsNode = objectMapper.createObjectNode();
                        if(namesArray.getAsString(i) != null)
                            jsNode.put("JS Name", namesArray.getAsString(i).toString());

                        // Here we check for a PdfStream object and retrieve the JS code from it
                        PdfObject jsCode = namesArray.get(i+1);
                        if (jsCode instanceof PdfStream) {
                            byte[] jsCodeBytes = ((PdfStream)jsCode).getBytes();
                            String jsCodeStr = new String(jsCodeBytes, StandardCharsets.UTF_8);
                            jsNode.put("JS Script Length", jsCodeStr.length());
                        } else if (jsCode instanceof PdfDictionary) {
                            // If the JS code is in a dictionary, you'll need to know the key to use.
                            // Assuming the key is PdfName.JS:
                            PdfStream jsCodeStream = ((PdfDictionary)jsCode).getAsStream(PdfName.JS);
                            if (jsCodeStream != null) {
                                byte[] jsCodeBytes = jsCodeStream.getBytes();
                                String jsCodeStr = new String(jsCodeBytes, StandardCharsets.UTF_8);
                                jsNode.put("JS Script Character Length", jsCodeStr.length());
                            }
                        }

                        javascriptArray.add(jsNode);
                    }

                }
            }
            other.set("JavaScript", javascriptArray);
            
            //TODO size
            PdfOCProperties ocProperties = itextDoc.getCatalog().getOCProperties(false);
            ArrayNode layersArray = objectMapper.createArrayNode();
            if (ocProperties != null) {
               
                for (PdfLayer layer : ocProperties.getLayers()) {
                    ObjectNode layerNode = objectMapper.createObjectNode();
                    layerNode.put("Name", layer.getPdfObject().getAsString(PdfName.Name).toString());
                    layersArray.add(layerNode);
                }
                
            }
            other.set("Layers", layersArray);
            
            //TODO Security
            

            
            
            
            
            // Digital Signatures using iText7 TODO
            

            
            
            PDStructureTreeRoot structureTreeRoot = pdfBoxDoc.getDocumentCatalog().getStructureTreeRoot();
            ArrayNode structureTreeArray;
			try {
				if(structureTreeRoot != null) {
					structureTreeArray = exploreStructureTree(structureTreeRoot.getKids());
					other.set("StructureTree", structureTreeArray);
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            
            
            boolean isPdfACompliant = checkOutputIntent(itextDoc, "PDF/A");
            boolean isPdfXCompliant = checkOutputIntent(itextDoc, "PDF/X");
            boolean isPdfECompliant = checkForStandard(itextDoc, "PDF/E");
            boolean isPdfVTCompliant = checkForStandard(itextDoc, "PDF/VT");
            boolean isPdfUACompliant = checkForStandard(itextDoc, "PDF/UA");
            boolean isPdfBCompliant = checkForStandard(itextDoc, "PDF/B"); // If you want to check for PDF/Broadcast, though this isn't an official ISO standard.
            boolean isPdfSECCompliant = checkForStandard(itextDoc, "PDF/SEC"); // This might not be effective since PDF/SEC was under development in 2021.
            
            compliancy.put("IsPDF/ACompliant", isPdfACompliant);
            compliancy.put("IsPDF/XCompliant", isPdfXCompliant);
            compliancy.put("IsPDF/ECompliant", isPdfECompliant);
            compliancy.put("IsPDF/VTCompliant", isPdfVTCompliant);
            compliancy.put("IsPDF/UACompliant", isPdfUACompliant);
            compliancy.put("IsPDF/BCompliant", isPdfBCompliant);
            compliancy.put("IsPDF/SECCompliant", isPdfSECCompliant);

     
            
           
            
            ArrayNode bookmarksArray = objectMapper.createArrayNode();
            PdfOutline root = itextDoc.getOutlines(false);
            if (root != null) {
                for (PdfOutline child : root.getAllChildren()) {
                    addOutlinesToArray(child, bookmarksArray);
                }
            }
            other.set("Bookmarks/Outline/TOC", bookmarksArray);
            
            byte[] xmpBytes = itextDoc.getXmpMetadata();
            String xmpString = null;
            if (xmpBytes != null) {
                try {
                    XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpBytes);
                    xmpString = new String(XMPMetaFactory.serializeToBuffer(xmpMeta, new SerializeOptions()));
                } catch (XMPException e) {
                    e.printStackTrace();
                }
            }
            other.put("XMPMetadata", xmpString);
            
            
            
            if (pdfBoxDoc.isEncrypted()) {
            	encryption.put("IsEncrypted", true);

                // Retrieve encryption details using getEncryption()
                PDEncryption pdfEncryption = pdfBoxDoc.getEncryption();
                encryption.put("EncryptionAlgorithm", pdfEncryption.getFilter());
                encryption.put("KeyLength", pdfEncryption.getLength());
                encryption.put("Permissions", pdfBoxDoc.getCurrentAccessPermission().toString());
                
                // Add other encryption-related properties as needed
            } else {
            	encryption.put("IsEncrypted", false);
            }
            
            
            

            ObjectNode pageInfoParent = objectMapper.createObjectNode();
            for (int pageNum = 1; pageNum <= itextDoc.getNumberOfPages(); pageNum++) {
                ObjectNode pageInfo = objectMapper.createObjectNode();

                // Page-level Information
                Rectangle pageSize = itextDoc.getPage(pageNum).getPageSize();
                pageInfo.put("Width", pageSize.getWidth());
                pageInfo.put("Height", pageSize.getHeight());
                pageInfo.put("Rotation", itextDoc.getPage(pageNum).getRotation());
                pageInfo.put("Page Orientation", getPageOrientation(pageSize.getWidth(),pageSize.getHeight())); 
                pageInfo.put("Standard Size", getPageSize(pageSize.getWidth(),pageSize.getHeight())); 
                
                // Boxes
                pageInfo.put("MediaBox", itextDoc.getPage(pageNum).getMediaBox().toString());
                pageInfo.put("CropBox", itextDoc.getPage(pageNum).getCropBox().toString());
                pageInfo.put("BleedBox", itextDoc.getPage(pageNum).getBleedBox().toString());
                pageInfo.put("TrimBox", itextDoc.getPage(pageNum).getTrimBox().toString());
                pageInfo.put("ArtBox", itextDoc.getPage(pageNum).getArtBox().toString());

                // Content Extraction
                PDFTextStripper textStripper = new PDFTextStripper();
                textStripper.setStartPage(pageNum -1);
                textStripper.setEndPage(pageNum - 1);
                String pageText = textStripper.getText(pdfBoxDoc);
                
                pageInfo.put("Text Characters Count", pageText.length()); //

             // Annotations
                List<PdfAnnotation> annotations = itextDoc.getPage(pageNum).getAnnotations();

                int subtypeCount = 0;
                int contentsCount = 0;

                for (PdfAnnotation annotation : annotations) {
                    if(annotation.getSubtype() != null) {
                        subtypeCount++;  // Increase subtype count
                    }
                    if(annotation.getContents() != null) {
                        contentsCount++;  // Increase contents count
                    }
                }

                ObjectNode annotationsObject = objectMapper.createObjectNode();
                annotationsObject.put("AnnotationsCount", annotations.size());
                annotationsObject.put("SubtypeCount", subtypeCount);
                annotationsObject.put("ContentsCount", contentsCount);
                pageInfo.set("Annotations", annotationsObject);
                
                // Images (simplified)
                // This part is non-trivial as images can be embedded in multiple ways in a PDF.
                // Here is a basic structure to recognize image XObjects on a page.
                ArrayNode imagesArray = objectMapper.createArrayNode();
                PdfResources resources = itextDoc.getPage(pageNum).getResources();
                for (PdfName name : resources.getResourceNames()) {
                    PdfObject obj = resources.getResource(name);
                    if (obj instanceof PdfStream) {
                        PdfStream stream = (PdfStream) obj;
                        if (PdfName.Image.equals(stream.getAsName(PdfName.Subtype))) {
                            ObjectNode imageNode = objectMapper.createObjectNode();
                            imageNode.put("Width", stream.getAsNumber(PdfName.Width).intValue());
                            imageNode.put("Height", stream.getAsNumber(PdfName.Height).intValue());
                            PdfObject colorSpace = stream.get(PdfName.ColorSpace);
                            if (colorSpace != null) {
                                imageNode.put("ColorSpace", colorSpace.toString());
                            }
                            imagesArray.add(imageNode);
                        }
                    }
                }
                pageInfo.set("Images", imagesArray);

                
                // Links
                ArrayNode linksArray = objectMapper.createArrayNode();
                Set<String> uniqueURIs = new HashSet<>();  // To store unique URIs

                for (PdfAnnotation annotation : annotations) {
                    if (annotation instanceof PdfLinkAnnotation) {
                        PdfLinkAnnotation linkAnnotation = (PdfLinkAnnotation) annotation;
                        if(linkAnnotation != null && linkAnnotation.getAction() != null) {
	                        String uri = linkAnnotation.getAction().toString();
	                        uniqueURIs.add(uri);  // Add to set to ensure uniqueness
                        }
                    }
                }

                // Add unique URIs to linksArray
                for (String uri : uniqueURIs) {
                    ObjectNode linkNode = objectMapper.createObjectNode();
                    linkNode.put("URI", uri);
                    linksArray.add(linkNode);
                }
                pageInfo.set("Links", linksArray);
                
             // Fonts
                ArrayNode fontsArray = objectMapper.createArrayNode();
                PdfDictionary fontDicts = resources.getResource(PdfName.Font);
                Set<String> uniqueSubtypes = new HashSet<>(); // To store unique subtypes

                // Map to store unique fonts and their counts
                Map<String, ObjectNode> uniqueFontsMap = new HashMap<>();

                if (fontDicts != null) {
                    for (PdfName key : fontDicts.keySet()) {
                        ObjectNode fontNode = objectMapper.createObjectNode(); // Create a new font node for each font
                        PdfDictionary font = fontDicts.getAsDictionary(key);

                        boolean isEmbedded = font.containsKey(PdfName.FontFile) ||
                                font.containsKey(PdfName.FontFile2) ||
                                font.containsKey(PdfName.FontFile3);
                        fontNode.put("IsEmbedded", isEmbedded);

                        if (font.containsKey(PdfName.Encoding)) {
                            String encoding = font.getAsName(PdfName.Encoding).toString();
                            fontNode.put("Encoding", encoding);
                        }

                        if (font.getAsString(PdfName.BaseFont) != null) {
                            fontNode.put("Name", font.getAsString(PdfName.BaseFont).toString());
                        }

                        String subtype = null;
                        if (font.containsKey(PdfName.Subtype)) {
                            subtype = font.getAsName(PdfName.Subtype).toString();
                            uniqueSubtypes.add(subtype);  // Add to set to ensure uniqueness
                        }
                        fontNode.put("Subtype", subtype);

                        PdfDictionary fontDescriptor = font.getAsDictionary(PdfName.FontDescriptor);
                        if (fontDescriptor != null) {
                            if (fontDescriptor.containsKey(PdfName.ItalicAngle)) {
                                fontNode.put("ItalicAngle", fontDescriptor.getAsNumber(PdfName.ItalicAngle).floatValue());
                            }

                            if (fontDescriptor.containsKey(PdfName.Flags)) {
                                int flags = fontDescriptor.getAsNumber(PdfName.Flags).intValue();
                                fontNode.put("IsItalic", (flags & 64) != 0);
                                fontNode.put("IsBold", (flags & 1 << 16) != 0);
                                fontNode.put("IsFixedPitch", (flags & 1) != 0);
                                fontNode.put("IsSerif", (flags & 2) != 0);
                                fontNode.put("IsSymbolic", (flags & 4) != 0);
                                fontNode.put("IsScript", (flags & 8) != 0);
                                fontNode.put("IsNonsymbolic", (flags & 16) != 0);
                            }

                            if (fontDescriptor.containsKey(PdfName.FontFamily)) {
                                String fontFamily = fontDescriptor.getAsString(PdfName.FontFamily).toString();
                                fontNode.put("FontFamily", fontFamily);
                            }

                            if (fontDescriptor.containsKey(PdfName.FontStretch)) {
                                String fontStretch = fontDescriptor.getAsName(PdfName.FontStretch).toString();
                                fontNode.put("FontStretch", fontStretch);
                            }

                            if (fontDescriptor.containsKey(PdfName.FontBBox)) {
                                PdfArray bbox = fontDescriptor.getAsArray(PdfName.FontBBox);
                                fontNode.put("FontBoundingBox", bbox.toString());
                            }

                            if (fontDescriptor.containsKey(PdfName.FontWeight)) {
                                float fontWeight = fontDescriptor.getAsNumber(PdfName.FontWeight).floatValue();
                                fontNode.put("FontWeight", fontWeight);
                            }
                        }

                        if (font.containsKey(PdfName.ToUnicode)) {
                            fontNode.put("HasToUnicodeMap", true);
                        }

                        if (fontNode.size() > 0) {
                            // Create a unique key for this font node based on its attributes
                            String uniqueKey = fontNode.toString(); 

                            // Increment count if this font exists, or initialize it if new
                            if (uniqueFontsMap.containsKey(uniqueKey)) {
                                ObjectNode existingFontNode = uniqueFontsMap.get(uniqueKey);
                                int count = existingFontNode.get("Count").asInt() + 1;
                                existingFontNode.put("Count", count);
                            } else {
                                fontNode.put("Count", 1);
                                uniqueFontsMap.put(uniqueKey, fontNode);
                            }
                        }
                    }
                }

                // Add unique font entries to fontsArray
                for (ObjectNode uniqueFontNode : uniqueFontsMap.values()) {
                    fontsArray.add(uniqueFontNode);
                }

                pageInfo.set("Fonts", fontsArray);
                
                
                
                
             // Access resources dictionary
                PdfDictionary resourcesDict = itextDoc.getPage(pageNum).getResources().getPdfObject();

                // Color Spaces & ICC Profiles
                ArrayNode colorSpacesArray = objectMapper.createArrayNode();
                PdfDictionary colorSpaces = resourcesDict.getAsDictionary(PdfName.ColorSpace);
                if (colorSpaces != null) {
                    for (PdfName name : colorSpaces.keySet()) {
                        PdfObject colorSpaceObject = colorSpaces.get(name);
                        if (colorSpaceObject instanceof PdfArray) {
                            PdfArray colorSpaceArray = (PdfArray) colorSpaceObject;
                            if (colorSpaceArray.size() > 1 && colorSpaceArray.get(0) instanceof PdfName && PdfName.ICCBased.equals(colorSpaceArray.get(0))) {
                                ObjectNode iccProfileNode = objectMapper.createObjectNode();
                                PdfStream iccStream = (PdfStream) colorSpaceArray.get(1);
                                byte[] iccData = iccStream.getBytes();
                                // TODO: Further decode and analyze the ICC data if needed
                                iccProfileNode.put("ICC Profile Length", iccData.length);
                                colorSpacesArray.add(iccProfileNode);
                            }
                        }
                    }
                }
                pageInfo.set("Color Spaces & ICC Profiles", colorSpacesArray);

                // Other XObjects
                Map<String, Integer> xObjectCountMap = new HashMap<>();  // To store the count for each type
                PdfDictionary xObjects = resourcesDict.getAsDictionary(PdfName.XObject);
                if (xObjects != null) {
                    for (PdfName name : xObjects.keySet()) {
                        PdfStream xObjectStream = xObjects.getAsStream(name);
                        String xObjectType = xObjectStream.getAsName(PdfName.Subtype).toString();

                        // Increment the count for this type in the map
                        xObjectCountMap.put(xObjectType, xObjectCountMap.getOrDefault(xObjectType, 0) + 1);
                    }
                }

                // Add the count map to pageInfo (or wherever you want to store it)
                ObjectNode xObjectCountNode = objectMapper.createObjectNode();
                for (Map.Entry<String, Integer> entry : xObjectCountMap.entrySet()) {
                    xObjectCountNode.put(entry.getKey(), entry.getValue());
                }
                pageInfo.set("XObjectCounts", xObjectCountNode);
                
         

                ArrayNode multimediaArray = objectMapper.createArrayNode();
                for (PdfAnnotation annotation : annotations) {
                    if (PdfName.RichMedia.equals(annotation.getSubtype())) {
                        ObjectNode multimediaNode = objectMapper.createObjectNode();
                        // Extract details from the dictionary as needed
                        multimediaArray.add(multimediaNode);
                    }
                }
                pageInfo.set("Multimedia", multimediaArray);

                

                pageInfoParent.set("Page " + pageNum, pageInfo);
            }

            
            jsonOutput.set("BasicInfo", basicInfo);
            jsonOutput.set("DocumentInfo", docInfoNode);
            jsonOutput.set("Compliancy", compliancy);
            jsonOutput.set("Encryption", encryption);
            jsonOutput.set("Other", other);
            jsonOutput.set("PerPageInfo", pageInfoParent);
            
            
            
            // Save JSON to file
            String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonOutput);
            
            
            
            return WebResponseUtils.bytesToWebResponse(jsonString.getBytes(StandardCharsets.UTF_8), "response.json", MediaType.APPLICATION_JSON);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
		return null;
    }

    private static void addOutlinesToArray(PdfOutline outline, ArrayNode arrayNode) {
        if (outline == null) return;
        ObjectNode outlineNode = objectMapper.createObjectNode();
        outlineNode.put("Title", outline.getTitle());
        // You can add other properties if needed
        arrayNode.add(outlineNode);
        
        for (PdfOutline child : outline.getAllChildren()) {
            addOutlinesToArray(child, arrayNode);
        }
    }
    public String getPageOrientation(double width, double height) {        
        if (width > height) {
            return "Landscape";
        } else if (height > width) {
            return "Portrait";
        } else {
            return "Square";
        }
    }
    public String getPageSize(double width, double height) {
        // Common aspect ratios used for standard paper sizes
        double[] aspectRatios = {4.0 / 3.0, 3.0 / 2.0, Math.sqrt(2.0), 16.0 / 9.0};

        // Check if the page matches any common aspect ratio
        for (double aspectRatio : aspectRatios) {
            if (isCloseToAspectRatio(width, height, aspectRatio)) {
                return "Standard";
            }
        }

        // If not a standard aspect ratio, consider it as a custom size
        return "Custom";
    }
    private boolean isCloseToAspectRatio(double width, double height, double aspectRatio) {
        // Calculate the aspect ratio of the page
        double pageAspectRatio = width / height;

        // Compare the page aspect ratio with the common aspect ratio within a threshold
        return Math.abs(pageAspectRatio - aspectRatio) <= 0.05;
    }
    
    public boolean checkForStandard(PdfDocument document, String standardKeyword) {
        // Check Output Intents
        boolean foundInOutputIntents = checkOutputIntent(document, standardKeyword);
        if (foundInOutputIntents) return true;

        // Check XMP Metadata (rudimentary)
        try {
            byte[] metadataBytes = document.getXmpMetadata();
            if (metadataBytes != null) {
                XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(metadataBytes);
                String xmpString = xmpMeta.dumpObject();
                if (xmpString.contains(standardKeyword)) {
                    return true;
                }
            }
        } catch (XMPException e) {
            e.printStackTrace();
        }

        return false;
    }


    public boolean checkOutputIntent(PdfDocument document, String standard) {
        PdfArray outputIntents = document.getCatalog().getPdfObject().getAsArray(PdfName.OutputIntents);
        if (outputIntents != null && !outputIntents.isEmpty()) {
            for (int i = 0; i < outputIntents.size(); i++) {
                PdfDictionary outputIntentDict = outputIntents.getAsDictionary(i);
                if (outputIntentDict != null) {
                    PdfString s = outputIntentDict.getAsString(PdfName.S);
                    if (s != null && s.toString().contains(standard)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    public ArrayNode exploreStructureTree(List<Object> nodes) {
        ArrayNode elementsArray = objectMapper.createArrayNode();
        if (nodes != null) {
            for (Object obj : nodes) {
                if (obj instanceof PDStructureNode) {
                    PDStructureNode node = (PDStructureNode) obj;
                    ObjectNode elementNode = objectMapper.createObjectNode();

                    if (node instanceof PDStructureElement) {
                        PDStructureElement structureElement = (PDStructureElement) node;
                        elementNode.put("Type", structureElement.getStructureType());
                        elementNode.put("Content", getContent(structureElement));

                        // Recursively explore child elements
                        ArrayNode childElements = exploreStructureTree(structureElement.getKids());
                        if (childElements.size() > 0) {
                            elementNode.set("Children", childElements);
                        }
                    }
                    elementsArray.add(elementNode);
                }
            }
        }
        return elementsArray;
    }


    public String getContent(PDStructureElement structureElement) {
        StringBuilder contentBuilder = new StringBuilder();

        for (Object item : structureElement.getKids()) {
            if (item instanceof COSString) {
                COSString cosString = (COSString) item;
                contentBuilder.append(cosString.getString());
            } else if (item instanceof PDStructureElement) {
                // For simplicity, we're handling only COSString and PDStructureElement here
                // but a more comprehensive method would handle other types too
                contentBuilder.append(getContent((PDStructureElement) item));
            }
        }

        return contentBuilder.toString();
    }
    
    
    private String formatDate(Calendar calendar) {
        if (calendar != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.format(calendar.getTime());
        } else {
            return null;
        }
    }

    private String getPageModeDescription(PdfName pageMode) {
        return pageMode != null ? pageMode.toString().replaceFirst("/", "") : "Unknown";
    }
}
