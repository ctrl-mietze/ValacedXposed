({
    default: vexcord.definePlugin({
        start({ vexcord: api }) {
            api.messages.transformOutgoing(content =>
                content.split("[demo]").join("Vexcord")
            )
        },
    }),
})
