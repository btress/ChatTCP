package org.example.ungdungchat.shared.protocol;

public class FileChunk {
    private String fileId;
    private String conversationId;
    private String fileName;
    private long totalSize;
    private int chunkIndex;
    private int totalChunks;
    private byte[] data;
    private String checksum;

    public FileChunk() {}

    public FileChunk(String fileId, String conversationId, String fileName, long totalSize, int chunkIndex, int totalChunks, byte[] data, String checksum) {
        this.fileId = fileId;
        this.conversationId = conversationId;
        this.fileName = fileName;
        this.totalSize = totalSize;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
        this.checksum = checksum;
    }

    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(int chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public int getTotalChunks() {
        return totalChunks;
    }

    public void setTotalChunks(int totalChunks) {
        this.totalChunks = totalChunks;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }
}
