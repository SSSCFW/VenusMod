package dev.ssscfw.venusmod.infrastructure.network.core;

/** 転送操作の画面内下書き。停止確認は対象に結びつけ、実行/認可は既存サーバーへ委譲する。 */
public final class TransferControlDraft {
    public static final int MAX_ID_TEXT = 20;
    private String idText = "";
    private TransferPriority priority = TransferPriority.NORMAL;
    private long pendingId;

    public String idText() { return idText; }
    public void idText(String value) {
        if (value == null) throw new IllegalArgumentException("転送IDを入力してください");
        idText = value;
        dismissCancellation();
    }
    public TransferPriority priority() { return priority; }
    public void priority(TransferPriority value) {
        if (value == null) throw new IllegalArgumentException("転送優先度を選択してください");
        priority = value;
        dismissCancellation();
    }
    public String priorityCommand() { return "transfer priority " + id() + " " + priority.label(); }
    public void requestCancellation() { pendingId = id(); }
    public boolean cancellationPending() { return pendingId != 0; }
    public void dismissCancellation() { pendingId = 0; }
    public String confirmCancellation() {
        if (pendingId == 0 || pendingId != id()) throw new IllegalArgumentException("対象を確認してから停止してください");
        String command = "transfer cancel " + pendingId;
        dismissCancellation();
        return command;
    }
    private long id() {
        if (idText.length() > MAX_ID_TEXT || idText.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("転送IDの長さまたは制御文字が不正です");
        String text = idText.strip();
        if (text.startsWith("#")) text = text.substring(1);
        if (!text.matches("[0-9]{1,19}")) throw new IllegalArgumentException("転送IDは正の整数です（例: #12）");
        try {
            long value = Long.parseLong(text);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("転送IDは1〜9223372036854775807です");
    }
}
