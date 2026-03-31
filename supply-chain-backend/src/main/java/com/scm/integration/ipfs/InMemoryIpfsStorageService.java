package com.scm.integration.ipfs;

import com.scm.common.util.HashUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev / CI friendly IPFS stand-in: deterministic CID from content hash, in-memory retrieval.
 */
@Service
@ConditionalOnProperty(name = "scm.ipfs.mode", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryIpfsStorageService implements IpfsStorageService {

    private final ConcurrentHashMap<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public String add(byte[] data, String fileName) {
        String hash = HashUtil.sha256Hex(data);
        String cid = "QmSTUB_" + hash.substring(0, Math.min(44, hash.length()));
        store.put(cid, data);
        return cid;
    }

    @Override
    public byte[] get(String cid) {
        if (cid == null) {
            return null;
        }
        return store.get(cid);
    }

    @Override
    public boolean isLocalStub() {
        return true;
    }
}
