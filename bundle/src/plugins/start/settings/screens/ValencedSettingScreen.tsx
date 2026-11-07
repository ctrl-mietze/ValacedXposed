import { Design } from '@valenced/discord/design'
import { SettingListRenderer } from '@valenced/discord/modules/settings/renderer'
import { ScrollView, View } from 'react-native'
import { Setting } from '../constants'

const { Card, Stack, Text } = Design

/** Discord-side settings, separate from the Android ValancedX manager. */
export default function ValencedSettingScreen() {
    return (
        <ScrollView style={{ flex: 1 }}>
            <Stack spacing={0}>
                <View style={{ padding: 16, paddingBottom: 6 }}>
                    <Card style={{ padding: 18 }}>
                        <Stack spacing={7}>
                            <Text variant="heading-lg/semibold" color="text-strong">
                                ValancedX
                            </Text>
                            <Text variant="text-md/medium">
                                Xposed-powered enhancements for your existing Discord app.
                            </Text>
                            <Text variant="text-sm/medium">
                                Configure the native hook and see diagnostics in the ValancedX Manager.
                            </Text>
                        </Stack>
                    </Card>
                </View>
                <View>
                    <SettingListRenderer.SettingsList
                        node={{
                            type: 'list',
                            sections: [
                                {
                                    label: 'ValancedX',
                                    settings: [
                                        Setting.ValencedVersion,
                                        Setting.LoaderVersion,
                                        Setting.ValencedSourceRepository,
                                        Setting.ValencedLicense,
                                    ],
                                },
                                {
                                    label: 'Runtime',
                                    settings: [
                                        Setting.ReactVersion,
                                        Setting.ReactNativeVersion,
                                        Setting.HermesVersion,
                                    ],
                                },
                                {
                                    label: 'Tools',
                                    settings: [
                                        Setting.Reload,
                                        Setting.ValencedEnterRecoveryMode,
                                    ],
                                },
                                {
                                    label: 'Advanced',
                                    settings: [Setting.ValencedDeveloperMode],
                                },
                            ],
                        }}
                    />
                </View>
            </Stack>
        </ScrollView>
    )
}
