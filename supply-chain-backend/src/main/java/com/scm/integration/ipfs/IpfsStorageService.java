package com.scm.integration.ipfs;

/**
 * Store and retrieve file bytes by content-addressed id (CID).
 * Production: java-ipfs-http-client; current stub uses in-memory map keyed by CID.
 */
public interface IpfsStorageService {

    /**
     * @return IPFS CID (or stub CID) for the stored object
     */
    String add(byte[] data, String fileName);

    /**
     * @return file bytes for the given CID, or null if missing
     */
    byte[] get(String cid);

    /**
     * Whether this implementation keeps bytes locally (stub) or streams from a node.
     */
    boolean isLocalStub();
}
