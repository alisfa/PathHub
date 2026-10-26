import { SiteAdapter } from './types';
import { GenericAdapter } from './generic';
import { SampleMediaAdapter } from './sample';
import { PornhubAdapter } from './pornhub';

export class AdapterRegistry {
  private static adapters: SiteAdapter[] = [
    new PornhubAdapter(),
    new SampleMediaAdapter(),
    new GenericAdapter() // Fallback must always be last
  ];

  public static getAdapterForHostname(hostname: string): SiteAdapter {
    const cleanHost = hostname.toLowerCase().replace(/^www\./, '');
    for (const adapter of this.adapters) {
      if (adapter.domains.includes('*')) continue;
      for (const domain of adapter.domains) {
        if (cleanHost === domain || cleanHost.endsWith('.' + domain)) {
          return adapter;
        }
      }
    }
    // Return generic fallback
    return this.adapters[this.adapters.length - 1];
  }
}
