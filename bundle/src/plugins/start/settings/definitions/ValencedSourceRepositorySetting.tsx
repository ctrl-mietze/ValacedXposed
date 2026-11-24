import TableRowAssetIcon from '@valenced/components/TableRowAssetIcon'
import { Linking } from 'react-native'
import { Setting } from '../constants'
import type { SettingsItem } from '@valenced/discord/modules/settings'

const ValencedSourceRepositorySetting: SettingsItem = {
    parent: Setting.Valenced,
    IconComponent: () => <TableRowAssetIcon name="PaperIcon" />,
    useTitle: () => 'Source Code',
    useDescription: () => 'https://github.com/ctrl-mietze/Vexcord',
    onPress: () => {
        Linking.openURL('https://github.com/ctrl-mietze/Vexcord')
    },
    type: 'pressable',
}

export default ValencedSourceRepositorySetting
