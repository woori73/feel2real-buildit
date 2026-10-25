// Shared aperture for the approved video and its compatibility projection.
export const VIDEO_APERTURE = {width:8.4,height:4.8,y:2.45,z:.15};

export function cameraFrame(width,height) {
  const compact=width<600;
  return {compact,distance:compact?8.7:Math.max(10,14/(width/Math.max(1,height))),targetY:2.2};
}

// Keep keyboard/touch targets inside the stage when the camera moves.
export function panelLabel(x,y,stageWidth,stageHeight) {
  const width=Math.min(148,Math.max(108,stageWidth*.205)),height=54;
  const left=Math.max(10,Math.min(stageWidth-width-10,x-width/2));
  const top=Math.max(64,Math.min(stageHeight-height-54,y-height/2));
  return {left,top,width,height};
}
