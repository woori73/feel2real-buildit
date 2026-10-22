import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';

export function mockOrigin(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' || !['localhost', '127.0.0.1'].includes(url.hostname)
      || !url.port || url.username || url.password) {
    throw new Error('MOCK_LOOPBACK_ORIGIN_REQUIRED: real institutions require separate approval and terms verification');
  }
  return url.origin;
}

export function encryptionKey(base64) {
  const key = Buffer.from(base64 || '', 'base64');
  if (key.length !== 32) throw new Error('EASY_COLLECTOR_FILE_KEY_BASE64 must contain a 256-bit key');
  return key;
}

/** Never uses the server's filename as a path; never writes a plaintext application file. */
export async function saveEncryptedDownload(download, directory, key) {
  const name = path.posix.basename(download.suggestedFilename().replaceAll('\\', '/'));
  if (!['.csv', '.xls', '.xlsx', '.pdf'].includes(path.extname(name).toLowerCase())) {
    throw new Error('UNSUPPORTED_DOWNLOAD_FORMAT');
  }
  const input = await download.createReadStream();
  if (!input) throw new Error('DOWNLOAD_STREAM_UNAVAILABLE');
  const chunks = [];
  let size = 0;
  for await (const chunk of input) {
    size += chunk.length;
    if (size > 10 * 1024 * 1024) {
      input.destroy();
      throw new Error('DOWNLOAD_SIZE_LIMIT');
    }
    chunks.push(chunk);
  }
  if (size === 0) throw new Error('EMPTY_DOWNLOAD');
  const plain = Buffer.concat(chunks);
  const sha256 = crypto.createHash('sha256').update(plain).digest('hex');
  const nonce = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce);
  const filename = `${crypto.randomUUID()}.enc`;
  cipher.setAAD(Buffer.from(filename));
  const encrypted = Buffer.concat([cipher.update(plain), cipher.final()]);
  await fs.mkdir(directory, { recursive: true, mode: 0o700 });
  const normalized = path.resolve(directory);
  if (await fs.realpath(normalized) !== normalized) throw new Error('DOWNLOAD_DIRECTORY_SYMLINK');
  await fs.chmod(directory, 0o700);
  await fs.writeFile(path.join(directory, filename),
    Buffer.concat([Buffer.from([1]), nonce, cipher.getAuthTag(), encrypted]), { flag: 'wx', mode: 0o600 });
  return { filename, size, sha256 };
}
