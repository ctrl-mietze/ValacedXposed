/** Vexcord SDK v1: additive public contract. Existing Valenced ABI stays intact. */
export type Dispose = () => void
export type RuntimeInfo = Readonly<{
    name: 'Vexcord'
    apiVersion: 1
    version: string
    platform: 'android'
    compatibility: 'valenced-v1'
}>
export type VexcordStorage<T extends Record<string, any>> = {
    get(): Promise<T>
    set(value: Partial<T>): Promise<void>
    replace(value: T): Promise<void>
    subscribe(listener: (value: T) => void): Dispose
}
export interface VexcordScope {
    readonly id: string
    readonly apiVersion: 1
    onCleanup(fn: Dispose): Dispose
    modules: {
        observe<T extends Record<string, any> = Record<string, any>>(properties: readonly string[], callback: (module: T) => void): Dispose
    }
    hooks: {
        before(target: object, method: string, transform: (args: any[]) => any[] | void): Dispose
        after(target: object, method: string, transform: (result: any) => any): Dispose
    }
    events: {
        observe<T extends object = Record<string, unknown>>(event: string, observer: (payload: T) => void): Dispose
    }
    storage: {
        open<T extends Record<string, any>>(name: string, defaults: T): VexcordStorage<T>
    }
    messages: {
        transformOutgoing(transform: (content: string, channelId: string) => string): Dispose
        transformEdit(transform: (content: string, channelId: string) => string): Dispose
    }
}
export interface VexcordDefinition {
    start?(api: {vexcord: VexcordScope}): void | Promise<void>
    stop?(api: {vexcord: VexcordScope}): void | Promise<void>
}
export interface VexcordRoot {
    readonly apiVersion: 1
    readonly name: 'Vexcord'
    readonly capabilities: readonly string[]
    runtimeInfo(): RuntimeInfo
    definePlugin<T extends VexcordDefinition>(options: T): T
}

declare global {
    const vexcord: VexcordRoot
}
