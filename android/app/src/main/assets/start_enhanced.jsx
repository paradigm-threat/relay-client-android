// Enhanced Test: Multi-component rendering with styling
// Tests: QuickJS, transpiler, nativeBridge, themed-styler, multiple views

(function () {
    'use strict';

    const { React, nativeBridge } = globalThis;

    if (!React) {
        console.error('React not available in globalThis');
        return;
    }

    if (!nativeBridge) {
        console.error('nativeBridge not available in globalThis');
        return;
    }

    console.log('Enhanced test: React available:', !!React);
    console.log('Enhanced test: nativeBridge available:', !!nativeBridge);

    // Create root container
    const rootTag = -1;
    const rootProps = {
        className: 'flex-1 bg-gray-100 w-full h-full p-4',
        width: 'match_parent',
        height: 'match_parent'
    };

    console.log('Creating root container...');
    nativeBridge.createView(rootTag, 'div', rootProps);

    // Create header
    const headerTag = 1;
    const headerProps = {
        className: 'bg-blue-500 p-4 w-full',
        width: 'match_parent',
        height: 'wrap_content'
    };
    nativeBridge.createView(headerTag, 'div', headerProps);

    // Header title
    const titleTag = 2;
    const titleProps = {
        text: 'Relay Client',
        className: 'text-white text-lg text-center',
        width: 'wrap_content',
        height: 'wrap_content'
    };
    nativeBridge.createView(titleTag, 'span', titleProps);
    nativeBridge.addChild(headerTag, titleTag, 0);

    // Add header to root
    nativeBridge.addChild(rootTag, headerTag, 0);

    // Create content area
    const contentTag = 3;
    const contentProps = {
        className: 'p-4 w-full',
        width: 'match_parent',
        height: 'wrap_content'
    };
    nativeBridge.createView(contentTag, 'div', contentProps);

    // Status text
    const statusTag = 4;
    const statusProps = {
        text: '✓ QuickJS Runtime',
        className: 'text-green-500 p-2',
        width: 'wrap_content',
        height: 'wrap_content'
    };
    nativeBridge.createView(statusTag, 'span', statusProps);
    nativeBridge.addChild(contentTag, statusTag, 0);

    // Transpiler status
    const transpilerTag = 5;
    const transpilerProps = {
        text: '✓ Hook Transpiler',
        className: 'text-green-500 p-2',
        width: 'wrap_content',
        height: 'wrap_content'
    };
    nativeBridge.createView(transpilerTag, 'span', transpilerProps);
    nativeBridge.addChild(contentTag, transpilerTag, 1);

    // Native bridge status
    const bridgeTag = 6;
    const bridgeProps = {
        text: '✓ Native Bridge',
        className: 'text-green-500 p-2',
        width: 'wrap_content',
        height: 'wrap_content'
    };
    nativeBridge.createView(bridgeTag, 'span', bridgeProps);
    nativeBridge.addChild(contentTag, bridgeTag, 2);

    // Add content to root
    nativeBridge.addChild(rootTag, contentTag, 1);

    // Create button (for future interactivity)
    const buttonTag = 7;
    const buttonProps = {
        text: 'Test Button',
        className: 'bg-blue-500 text-white p-4 m-4',
        width: 'wrap_content',
        height: 'wrap_content'
    };
    nativeBridge.createView(buttonTag, 'button', buttonProps);
    nativeBridge.addChild(rootTag, buttonTag, 2);

    // Add click handler to button
    let clickCount = 0;
    nativeBridge.addEventListener(buttonTag, 'click', function (data) {
        clickCount++;
        console.log('Button clicked! Count:', clickCount);

        // Update button text to show click count
        nativeBridge.updateProps(buttonTag, {
            text: 'Clicked ' + clickCount + ' times',
            className: clickCount % 2 === 0 ? 'bg-blue-500 text-white p-4 m-4' : 'bg-green-500 text-white p-4 m-4'
        });
    });

    console.log('Enhanced test: Rendering complete!');
})();
