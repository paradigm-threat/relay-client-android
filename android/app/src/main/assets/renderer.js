/**
 * Custom React Host Renderer for Pure Android
 * Intended to be executed inside the embedded QuickJS runtime.
 */

const { DefaultReconciler } = require('react-reconciler');

const hostConfig = {
  now: Date.now,
  supportsMutation: true,
  createInstance(type, props, rootContainerInstance, hostContext, internalInstanceHandle) {
    const tag = generateTag();
    nativeBridge.createView(tag, type, props);
    return tag;
  },
  createTextInstance(text, rootContainerInstance, hostContext, internalInstanceHandle) {
    const tag = generateTag();
    nativeBridge.createView(tag, 'Text', { text });
    return tag;
  },
  appendChildToContainer(container, child) {
    nativeBridge.addChild(container, child, -1);
  },
  appendChild(parent, child) {
    nativeBridge.addChild(parent, child, -1);
  },
  appendInitialChild(parent, child) {
    nativeBridge.addChild(parent, child, -1);
  },
  removeChildFromContainer(container, child) {
    nativeBridge.removeChild(container, child);
  },
  removeChild(parent, child) {
    nativeBridge.removeChild(parent, child);
  },
  insertInContainerBefore(container, child, beforeChild) {
    nativeBridge.addChild(container, child, -2); // -2 or index logic
  },
  insertBefore(parent, child, beforeChild) {
    nativeBridge.addChild(parent, child, -2);
  },
  prepareUpdate(instance, type, oldProps, newProps, rootContainerInstance, hostContext) {
    return true;
  },
  commitUpdate(instance, updatePayload, type, oldProps, newProps, internalInstanceHandle) {
    nativeBridge.updateProps(instance, newProps);
  },
  finalizeInitialChildren() { return false; },
  getChildHostContext() { return {}; },
  getRootHostContext() { return {}; },
  getPublicInstance(instance) { return instance; },
  prepareForCommit() { return null; },
  resetAfterCommit() { },
  shouldSetTextContent() { return false; },
};

let tagCounter = 1;
function generateTag() {
  return tagCounter++;
}

const AndroidRenderer = DefaultReconciler(hostConfig);

module.exports = {
  render(element, container) {
    const root = AndroidRenderer.createContainer(container, 0, false, null);
    AndroidRenderer.updateContainer(element, root, null, null);
  }
};
