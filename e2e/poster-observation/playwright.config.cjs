const {defineConfig}=require('@playwright/test');
const path=require('node:path');
const outputDir=path.resolve(__dirname,'../../poster-observation-results');
module.exports=defineConfig({testDir:__dirname,testMatch:'*.spec.cjs',workers:1,retries:0,timeout:20000,
  use:{browserName:'chromium',viewport:{width:390,height:900},launchOptions:process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}},
  outputDir,reporter:[['list'],['json',{outputFile:path.join(outputDir,'results.json')}]]});
