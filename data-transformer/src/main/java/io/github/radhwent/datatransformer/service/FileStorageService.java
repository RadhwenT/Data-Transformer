package io.github.radhwent.datatransformer.service;

import io.github.radhwent.datatransformer.entity.FileFormat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.xml.XmlMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final XmlMapper xmlMapper = new XmlMapper();

    /** Element racine utilise quand on ecrit plusieurs enregistrements en XML. */
    private static final String XML_ROOT = "records";

    @Value("${app.storage.dir}")
    private String storageDir;

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /**
     * Lit un fichier (JSON ou XML) et le convertit en liste d'enregistrements.
     * Un fichier contenant un seul objet renvoie une liste d'un element :
     * le reste de la chaine traite toujours une liste, sans cas particulier.
     */
    public List<Map<String, Object>> readRecords(MultipartFile file, FileFormat format) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return format == FileFormat.XML ? readXml(in) : readJson(in);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readJson(InputStream in) throws IOException {
        Object parsed = jsonMapper.readValue(in, Object.class);

        if (parsed instanceof List<?> list) {
            List<Map<String, Object>> records = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map)) {
                    throw new InvalidFileFormatException(
                            "Le tableau doit contenir des objets, pas des valeurs simples");
                }
                records.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
            return records;
        }

        if (parsed instanceof Map) {
            return new ArrayList<>(List.of(new LinkedHashMap<>((Map<String, Object>) parsed)));
        }

        throw new InvalidFileFormatException("Format invalide, objet ou tableau JSON attendu");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readXml(InputStream in) throws IOException {
        Map<String, Object> root = xmlMapper.readValue(in, LinkedHashMap.class);
        List<Map<String, Object>> records = new ArrayList<>();

        // Cas 1 : la racine contient une liste d'elements enfants repetes
        //         (ex. <records><record>..</record><record>..</record></records>)
        if (root.size() == 1) {
            Object unique = root.values().iterator().next();

            if (unique instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map) {
                        records.add(new LinkedHashMap<>((Map<String, Object>) item));
                    }
                }
                return records;
            }

            if (unique instanceof Map) {
                records.add(new LinkedHashMap<>((Map<String, Object>) unique));
                return records;
            }
        }

        // Cas 2 : la racine est directement l'enregistrement
        //         (ex. <client><prenom>sarah</prenom>...</client>)
        records.add(new LinkedHashMap<>(root));
        return records;
    }

    // ------------------------------------------------------------------
    // Ecriture
    // ------------------------------------------------------------------

    /** Sauvegarde le fichier d'entree tel quel et renvoie son chemin. */
    public String saveInputFile(MultipartFile file) throws IOException {
        Path dir = createStorageDirIfNeeded();
        String fileName = UUID.randomUUID() + "_" + sanitizeFileName(file.getOriginalFilename());
        Path target = dir.resolve(fileName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        return target.toString();
    }

    /**
     * Ecrit le resultat transforme dans le format demande.
     * Un seul enregistrement produit un objet simple, plusieurs produisent
     * un tableau : la sortie reste lisible dans les deux cas.
     */
    public String writeOutputFile(List<Map<String, Object>> records,
                                  FileFormat format,
                                  String originalName) throws IOException {
        Path dir = createStorageDirIfNeeded();
        String extension = format == FileFormat.XML ? ".xml" : ".json";
        String baseName = stripExtension(sanitizeFileName(originalName));
        Path target = dir.resolve(UUID.randomUUID() + "_output_" + baseName + extension);

        Object payload = records.size() == 1 ? records.get(0) : records;

        if (format == FileFormat.XML) {
            xmlMapper.writer()
                    .withRootName(records.size() == 1 ? "record" : XML_ROOT)
                    .withDefaultPrettyPrinter()
                    .writeValue(target.toFile(), payload);
        } else {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), payload);
        }

        return target.toString();
    }

    public byte[] readFileContent(String path) throws IOException {
        return Files.readAllBytes(Paths.get(path));
    }

    // ------------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------------

    private Path createStorageDirIfNeeded() throws IOException {
        Path dir = Paths.get(storageDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    /**
     * Ne garde que le nom de fichier (sans repertoires), pour empecher un
     * nom d'origine du type "../../etc/passwd" d'ecrire hors de storageDir.
     */
    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "fichier";
        }
        return Paths.get(originalName).getFileName().toString();
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}