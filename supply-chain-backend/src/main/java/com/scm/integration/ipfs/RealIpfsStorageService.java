package com.scm.integration.ipfs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * Connects to a real Kubo (go-ipfs) node via its HTTP RPC API on port 5001.
 * Activated when scm.ipfs.mode=node.
 */
@Service
@ConditionalOnProperty(name = "scm.ipfs.mode", havingValue = "node")
public class RealIpfsStorageService implements IpfsStorageService {

    private static final Logger log = LoggerFactory.getLogger(RealIpfsStorageService.class);

    @Value("${scm.ipfs.api-url:http://127.0.0.1:5001}")
    private String apiUrl;

    private final RestTemplate rest = new RestTemplate();

    @PostConstruct
    public void init() {
        log.info("IPFS mode = node, API endpoint = {}", apiUrl);
        try {
            ResponseEntity<Map> resp = rest.postForEntity(apiUrl + "/api/v0/id", null, Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                log.info("Connected to IPFS node: PeerID={}", resp.getBody().get("ID"));
            }
        } catch (Exception e) {
            log.warn("Cannot reach IPFS node at {} — uploads will fail until the node is started. Error: {}",
                    apiUrl, e.getMessage());
        }
    }

    @Override
    public String add(byte[] data, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ByteArrayResource resource = new ByteArrayResource(data) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", resource);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> resp = rest.postForEntity(
                apiUrl + "/api/v0/add?pin=true&quieter=false",
                request,
                Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("IPFS add failed: HTTP " + resp.getStatusCodeValue());
        }

        String cid = (String) resp.getBody().get("Hash");
        log.info("IPFS add OK — CID={}, name={}, size={}", cid, fileName, resp.getBody().get("Size"));
        return cid;
    }

    @Override
    public byte[] get(String cid) {
        if (cid == null || cid.trim().isEmpty()) {
            return null;
        }
        try {
            ResponseEntity<byte[]> resp = rest.postForEntity(
                    apiUrl + "/api/v0/cat?arg=" + cid,
                    null,
                    byte[].class
            );
            return resp.getBody();
        } catch (Exception e) {
            log.error("IPFS cat failed for CID={}: {}", cid, e.getMessage());
            return null;
        }
    }

    @Override
    public boolean isLocalStub() {
        return false;
    }
}
