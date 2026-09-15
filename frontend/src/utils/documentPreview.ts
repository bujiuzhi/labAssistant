/** 文档预览格式分流、文本解码与 CSV 轻量解析能力。 */

export type DocumentPreviewMode =
  | "docx"
  | "spreadsheet"
  | "pptx"
  | "pdf"
  | "native-pdf"
  | "image"
  | "text"
  | "unsupported";

export const MAX_COMPONENT_PREVIEW_BYTES = 25 * 1024 * 1024;
export const MAX_TEXT_PREVIEW_BYTES = 5 * 1024 * 1024;

const imageExtensions = new Set(["bmp", "gif", "jpeg", "jpg", "png", "webp"]);
const convertedOfficeExtensions = new Set([
  "doc",
  "odp",
  "ods",
  "odt",
  "ppt",
  "rtf",
]);

/**
 * 根据格式和文件体积选择首选预览实现
 *
 * @param extension 小写或混合大小写文件扩展名
 * @param fileSize 文件字节数
 * @returns 预览模式
 */
export function resolveDocumentPreviewMode(
  extension: string,
  fileSize: number,
): DocumentPreviewMode {
  const normalizedExtension = extension.trim().toLowerCase().replace(/^\./, "");
  if (imageExtensions.has(normalizedExtension)) return "image";
  // Vue Office PDF 默认配置会引用外部静态资源；生产环境统一使用同源原生预览。
  if (normalizedExtension === "pdf") return "native-pdf";
  if (["txt", "csv"].includes(normalizedExtension)) {
    return fileSize > MAX_TEXT_PREVIEW_BYTES ? "unsupported" : "text";
  }
  if (convertedOfficeExtensions.has(normalizedExtension)) return "unsupported";
  if (fileSize > MAX_COMPONENT_PREVIEW_BYTES) {
    return "unsupported";
  }
  if (normalizedExtension === "docx") return "docx";
  if (["xls", "xlsx"].includes(normalizedExtension)) return "spreadsheet";
  if (normalizedExtension === "pptx") return "pptx";
  return "unsupported";
}

/**
 * 解码 UTF-8、UTF-16 或常见中文编码文本
 *
 * @param buffer 文件二进制内容
 * @returns 解码后的文本
 */
export function decodeTextDocument(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  if (bytes[0] === 0xff && bytes[1] === 0xfe) {
    return new TextDecoder("utf-16le").decode(bytes.subarray(2));
  }
  if (bytes[0] === 0xfe && bytes[1] === 0xff) {
    return new TextDecoder("utf-16be").decode(bytes.subarray(2));
  }
  const utf8Bytes =
    bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf
      ? bytes.subarray(3)
      : bytes;
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(utf8Bytes);
  } catch {
    return new TextDecoder("gb18030").decode(bytes);
  }
}

/**
 * 将 CSV 文本解析为有界二维表，避免超大文件阻塞页面
 *
 * @param text CSV 原始文本
 * @param maxRows 最大预览行数
 * @param maxColumns 最大预览列数
 * @returns CSV 单元格二维数组
 */
export function parseCsvPreview(
  text: string,
  maxRows = 200,
  maxColumns = 50,
): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let inQuotes = false;

  const pushField = (): void => {
    if (row.length < maxColumns) row.push(field);
    field = "";
  };
  const pushRow = (): void => {
    pushField();
    if (row.some((value) => value.length > 0)) rows.push(row);
    row = [];
  };

  for (let index = 0; index < text.length && rows.length < maxRows; index += 1) {
    const character = text[index] ?? "";
    if (character === '"') {
      if (inQuotes && text[index + 1] === '"') {
        field += '"';
        index += 1;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (character === "," && !inQuotes) {
      pushField();
    } else if ((character === "\n" || character === "\r") && !inQuotes) {
      if (character === "\r" && text[index + 1] === "\n") index += 1;
      pushRow();
    } else {
      field += character;
    }
  }
  if (rows.length < maxRows && (field.length > 0 || row.length > 0)) pushRow();
  return rows;
}
