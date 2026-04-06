package bump;

final class EnumVariantInfo {
    final SemanticType enumType;
    final String payloadName;
    final SemanticType payloadType;

    EnumVariantInfo(SemanticType enumType, String payloadName, SemanticType payloadType) {
        this.enumType = enumType;
        this.payloadName = payloadName;
        this.payloadType = payloadType;
    }
}
