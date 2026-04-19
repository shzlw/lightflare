#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const rootDir = path.resolve(import.meta.dirname, "..");
const versionFile = path.join(rootDir, "VERSION");

const args = process.argv.slice(2);
const checkOnly = args.includes("--check");
const explicitVersion = args.find((arg) => !arg.startsWith("--"));
const version = explicitVersion ?? readVersionFile();

if (!/^[0-9A-Za-z][0-9A-Za-z._-]*$/.test(version)) {
  fail(`Invalid version "${version}". Use a Docker-tag-safe value like 0.2.0 or 1.0.0-rc1.`);
}

const changed = new Set();

writeText("VERSION", `${version}\n`);

updateServerVersion(version);
updatePackageVersion("webui/package.json", version);
updatePackageLockVersion("webui/package-lock.json", version);
updatePackageVersion("docs/package.json", version);
updatePackageLockVersion("docs/package-lock.json", version);

replaceInFile("compose.yaml", /shzlwio\/lightflare:[0-9A-Za-z._-]+/g, `shzlwio/lightflare:${version}`);
replaceInFile("start.sh", /lightflare-app-[0-9A-Za-z._-]+\.jar/g, `lightflare-app-${version}.jar`);
replaceInFile(
  "docs/docs/installation.mdx",
  /shzlwio\/lightflare:[0-9A-Za-z._-]+/g,
  `shzlwio/lightflare:${version}`,
);
replaceInFile(
  "docs/docs/installation.mdx",
  /lightflare-app-[0-9A-Za-z._-]+\.jar/g,
  `lightflare-app-${version}.jar`,
);

if (checkOnly && changed.size > 0) {
  fail(`Version files are out of sync for ${version}:\n${[...changed].map((file) => `  ${file}`).join("\n")}`);
}

if (changed.size > 0) {
  console.log(`Synced Lightflare version ${version}`);
  for (const file of changed) {
    console.log(`  ${file}`);
  }
} else {
  console.log(`Lightflare version ${version} is already in sync`);
}

function readVersionFile() {
  try {
    return fs.readFileSync(versionFile, "utf8").trim();
  } catch {
    fail("VERSION file is missing. Pass a version explicitly, for example: node scripts/sync-version.mjs 0.2.0");
  }
}

function updateServerVersion(nextVersion) {
  replaceInFile("server/pom.xml", /<revision>[^<]+<\/revision>/g, `<revision>${nextVersion}</revision>`);
}

function updatePackageVersion(file, nextVersion) {
  updateJson(file, (data) => {
    data.version = nextVersion;
    return data;
  });
}

function updatePackageLockVersion(file, nextVersion) {
  updateJson(file, (data) => {
    data.version = nextVersion;
    if (data.packages?.[""]) {
      data.packages[""].version = nextVersion;
    }
    return data;
  });
}

function updateJson(file, updater) {
  const absolutePath = path.join(rootDir, file);
  const before = fs.readFileSync(absolutePath, "utf8");
  const after = `${JSON.stringify(updater(JSON.parse(before)), null, 2)}\n`;
  writeIfChanged(file, before, after);
}

function replaceInFile(file, pattern, replacement) {
  const absolutePath = path.join(rootDir, file);
  const before = fs.readFileSync(absolutePath, "utf8");
  const after = before.replace(pattern, replacement);
  writeIfChanged(file, before, after);
}

function writeText(file, content) {
  const absolutePath = path.join(rootDir, file);
  const before = fs.existsSync(absolutePath) ? fs.readFileSync(absolutePath, "utf8") : "";
  writeIfChanged(file, before, content);
}

function writeIfChanged(file, before, after) {
  if (before === after) {
    return;
  }

  changed.add(file);
  if (!checkOnly) {
    fs.writeFileSync(path.join(rootDir, file), after);
  }
}

function fail(message) {
  console.error(message);
  process.exit(1);
}
