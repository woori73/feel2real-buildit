export const api = (path: string, init?: RequestInit) =>
  fetch('/problem-generator/wiki/api/' + path.replace(/^\/+/, ''), init)
