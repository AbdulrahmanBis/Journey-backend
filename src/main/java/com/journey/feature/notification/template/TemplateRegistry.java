package com.journey.feature.notification.template;

import tools.jackson.databind.ObjectMapper;
import com.journey.common.enums.NotificationChannel;
import jakarta.annotation.PostConstruct;
import tools.jackson.core.JacksonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads notification templates from disk.
 *
 * <p>Layout, one directory per template:
 *
 * <pre>
 * notifications/templates/
 *   journey-assigned/
 *     config.json          who/which channels/which variables
 *     en/ push.json        { "title": "...", "body": "..." }   → the bell
 *         mail.xml         &lt;mail&gt;&lt;subject/&gt;&lt;body/&gt;&lt;/mail&gt;      → the email
 *     ar/ push.json
 *         mail.xml
 * </pre>
 *
 * <p>Templates live on the filesystem rather than in the jar so wording can be corrected without a
 * rebuild — the same reasoning as {@code app.storage.local.root}. Files are cached and reloaded
 * automatically when their modification time changes, so an edit takes effect on the next
 * notification without a restart.
 *
 * <p>Every template is loaded and validated at startup. A malformed template is a boot failure,
 * which is the right time to find out — the alternative is discovering it when the first learner is
 * assigned a journey.
 */
@Slf4j
@Component
public class TemplateRegistry {

    public static final String DEFAULT_LANGUAGE = "en";

    /** Both are required, matching the parity rule the front end's i18n files follow. */
    static final List<String> LANGUAGES = List.of("en", "ar");

    /**
     * Variables the dispatcher always supplies, so templates may use them without declaring them:
     * {@code link} (the rendered target route) and {@code appUrl} (the site origin, for email).
     */
    static final Set<String> BUILT_IN_VARIABLES = Set.of("link", "appUrl");

    /** Template ids become path segments, so they are restricted rather than sanitised. */
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");

    private final Path root;
    private final ObjectMapper mapper;
    private final TemplateRenderer renderer;
    private final Map<String, NotificationTemplate> cache = new ConcurrentHashMap<>();

    public TemplateRegistry(
            @Value("${app.notifications.templates-root:./notifications/templates}") String root,
            ObjectMapper mapper,
            TemplateRenderer renderer) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.mapper = mapper;
        this.renderer = renderer;
    }

    /** Loads and validates every template so problems surface at boot, not at send time. */
    @PostConstruct
    void loadAll() {
        if (!Files.isDirectory(root)) {
            log.warn("Notification templates directory not found at {} — no templates loaded.", root);
            return;
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                get(dir.getFileName().toString());
                ids.add(dir.getFileName().toString());
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read notification templates from " + root, e);
        }
        log.info("Loaded {} notification template(s) from {}: {}", ids.size(), root, ids);
    }

    /** The named template, loading it if it is not cached or its files changed on disk. */
    public NotificationTemplate get(String templateId) {
        if (templateId == null || !VALID_ID.matcher(templateId).matches()) {
            throw new IllegalArgumentException("Invalid notification template id: " + templateId);
        }
        long signature = signatureOf(templateId);
        NotificationTemplate cached = cache.get(templateId);
        if (cached != null && cached.signature() == signature) {
            return cached;
        }
        NotificationTemplate loaded = load(templateId, signature);
        cache.put(templateId, loaded);
        return loaded;
    }

    // ─── Loading ──────────────────────────────────────────────────────────────

    private NotificationTemplate load(String templateId, long signature) {
        Path dir = root.resolve(templateId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("No notification template directory: " + dir);
        }

        TemplateConfig config = readConfig(dir.resolve("config.json"));
        Set<NotificationChannel> channels = resolveChannels(templateId, config);

        Map<String, NotificationTemplate.MessageContent> inApp = new HashMap<>();
        Map<String, NotificationTemplate.MailContent> mail = new HashMap<>();

        boolean mailRequired = channels.contains(NotificationChannel.MAIL);
        for (String language : LANGUAGES) {
            inApp.put(language, readPush(dir.resolve(language).resolve("push.json"), templateId));

            // Required when the template sends mail; otherwise still validated if it is there, so a
            // mail.xml written ahead of enabling the channel is checked rather than silently ignored.
            Path mailFile = dir.resolve(language).resolve("mail.xml");
            if (mailRequired || Files.isRegularFile(mailFile)) {
                mail.put(language, readMail(mailFile, templateId));
            }
        }

        NotificationTemplate template =
                new NotificationTemplate(templateId, config, Map.copyOf(inApp), Map.copyOf(mail), signature);
        validatePlaceholders(template);
        return template;
    }

    private TemplateConfig readConfig(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing notification template config: " + file);
        }
        try {
            TemplateConfig config = mapper.readValue(Files.readString(file), TemplateConfig.class);
            if (config.channels().isEmpty()) {
                throw new IllegalStateException(file + " lists no channels.");
            }
            if (config.recipients().isEmpty()) {
                throw new IllegalStateException(file + " lists no recipients.");
            }
            return config;
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private Set<NotificationChannel> resolveChannels(String templateId, TemplateConfig config) {
        Set<NotificationChannel> channels = new HashSet<>();
        for (String key : config.channels()) {
            try {
                channels.add(NotificationChannel.fromKey(key));
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Template " + templateId + ": " + e.getMessage(), e);
            }
        }
        return channels;
    }

    private NotificationTemplate.MessageContent readPush(Path file, String templateId) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "Template " + templateId + " is missing " + root.relativize(file)
                            + ". Both languages are required.");
        }
        try {
            NotificationTemplate.MessageContent content =
                    mapper.readValue(Files.readString(file), NotificationTemplate.MessageContent.class);
            if (isBlank(content.title()) || isBlank(content.body())) {
                throw new IllegalStateException(file + " needs both a title and a body.");
            }
            return content;
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    private NotificationTemplate.MailContent readMail(Path file, String templateId) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "Template " + templateId + " sends MAIL but is missing " + root.relativize(file) + ".");
        }
        try {
            Document doc = secureDocumentBuilder().parse(file.toFile());
            String subject = firstText(doc, "subject");
            String body = firstText(doc, "body");
            if (isBlank(subject) || isBlank(body)) {
                throw new IllegalStateException(file + " needs both <subject> and <body>.");
            }
            return new NotificationTemplate.MailContent(subject, body);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * XML parser with external entities disabled. These files are ours, but an XML parser that
     * resolves entities is an XXE primitive and there is no reason to leave one switched on.
     */
    private DocumentBuilder secureDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    private String firstText(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    /**
     * Both directions of the variable contract: nothing may be used that was not declared, and
     * nothing should be declared that is never used.
     */
    private void validatePlaceholders(NotificationTemplate template) {
        Set<String> declared = new HashSet<>(template.config().variables());
        Set<String> used = new HashSet<>();

        for (NotificationTemplate.MessageContent content : template.inApp().values()) {
            used.addAll(renderer.placeholdersIn(content.title(), content.body()));
        }
        for (NotificationTemplate.MailContent content : template.mail().values()) {
            used.addAll(renderer.placeholdersIn(content.subject(), content.body()));
        }
        used.addAll(renderer.placeholdersIn(template.config().link()));

        Set<String> undeclared = new HashSet<>(used);
        undeclared.removeAll(declared);
        undeclared.removeAll(BUILT_IN_VARIABLES);
        if (!undeclared.isEmpty()) {
            throw new IllegalStateException("Template " + template.id()
                    + " uses undeclared variable(s) " + undeclared
                    + ". Add them to \"variables\" in config.json.");
        }

        Set<String> unused = new HashSet<>(declared);
        unused.removeAll(used);
        unused.removeAll(BUILT_IN_VARIABLES);
        if (!unused.isEmpty()) {
            log.warn("Template {} declares variable(s) {} that no content uses.", template.id(), unused);
        }
    }

    // ─── Change detection ─────────────────────────────────────────────────────

    /**
     * A cheap fingerprint of the template's files. Any edit, addition or removal changes it, which
     * is enough to know the cached copy is stale.
     */
    private long signatureOf(String templateId) {
        Path dir = root.resolve(templateId);
        long signature = 17L;
        signature = 31 * signature + modifiedAt(dir.resolve("config.json"));
        for (String language : LANGUAGES) {
            signature = 31 * signature + modifiedAt(dir.resolve(language).resolve("push.json"));
            signature = 31 * signature + modifiedAt(dir.resolve(language).resolve("mail.xml"));
        }
        return signature;
    }

    private long modifiedAt(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
