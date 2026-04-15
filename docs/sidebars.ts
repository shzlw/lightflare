import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

/**
 * Creating a sidebar enables you to:
 - create an ordered group of docs
 - render a sidebar for each doc of that group
 - provide next/previous navigation

 The sidebars can be generated from the filesystem, or explicitly defined here.

 Create as many sidebars as you want.
 */
const sidebars: SidebarsConfig = {
  tutorialSidebar: [
    'intro',
    'installation',
    {
      type: 'category',
      label: 'Capabilities',
      link: {type: 'doc', id: 'capabilities'},
      items: [
        'capabilities/memory',
        'capabilities/chat',
        'capabilities/agent',
        'capabilities/scheduling',
        'capabilities/mcp',
        'capabilities/logging',
      ],
    },
    {
      type: 'category',
      label: 'LLM Providers',
      link: {type: 'doc', id: 'llm-providers'},
      items: ['llm-providers/openai', 'llm-providers/ollama', 'llm-providers/openrouter'],
    },
    {
      type: 'category',
      label: 'Tools',
      link: {type: 'doc', id: 'tools'},
      items: ['tools/slack', 'tools/openmeteo', 'tools/playwright', 'tools/brave-search'],
    },
  ],
};

export default sidebars;
