const {defineConfig}=require('@playwright/test');
module.exports=defineConfig({testDir:__dirname,testMatch:'*.spec.cjs',workers:1,retries:0,timeout:20000,
  use:{browserName:'chromium',viewport:{width:390,height:900},launchOptions:process.env.CHROMIUM_PATH?{executablePath:process.env.CHROMIUM_PATH}:{}},
  outputDir:'../../poster-observation-results',reporter:[['list'],['json',{outputFile:'poster-observation-results/results.json'}]]});
