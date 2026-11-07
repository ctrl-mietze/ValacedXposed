import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { reloadApp } from '@valenced/modules/native/app'
import {
    isDefaultsOnlyBoot,
    requestNextBootDefaultsOnly,
} from '@valenced/plugins/_'
import { Setting } from '~plugins/start/settings/constants'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedEnterRecoveryModeSetting: SettingsItem = {
    parent: Setting.Valenced,
    type: 'pressable',
    useIsDisabled: () => isDefaultsOnlyBoot,
    useTitle: () => 'Enter Recovery Mode',
    useDescription: () =>
        isDefaultsOnlyBoot
            ? 'Currently in Recovery Mode. Reload to exit.'
            : 'Start once with bundled plugins only. Useful when a third-party plugin misbehaves.',
    IconComponent: () => <TableRowAssetIcon name="ShieldIcon" />,
    onPress: () => {
        requestNextBootDefaultsOnly()
        reloadApp()
    },
}

export default ValencedEnterRecoveryModeSetting
