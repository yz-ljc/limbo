'use strict'
const mc = require('minecraft-protocol')
const minecraftData = require('minecraft-data')
const assert = require('node:assert/strict')
const versions = process.argv.slice(2)
if (!versions.length) versions.push('1.8', '1.20.1', '1.21.4', '26.1')

async function probe(version, index) {
  return new Promise((resolve, reject) => {
    const md = minecraftData(version)
    const pv = md.version.version
    const modernHat = pv >= 769
    const customPackets = {}
    if (modernHat) {
      // minecraft-data@3.117.0 swaps the last two action flags. Verified against
      // BungeeCord's PlayerListItemUpdate.Action: LIST_ORDER=6, HAT=7.
      const info = structuredClone(md.protocol.play.toClient.types.packet_player_info)
      info[1][0].type[1].flags = ['add_player', 'initialize_chat', 'update_game_mode', 'update_listed',
        'update_latency', 'update_display_name', 'update_list_order', 'update_hat']
      customPackets[md.version.majorVersion] = { play: { toClient: { types: { packet_player_info: info } } } }
    }
    const client = mc.createClient({ host: '127.0.0.1', port: Number(process.env.LIMBO_PORT || 25677),
      username: 'SkinProbe' + index, version, auth: 'offline', hideErrors: true, customPackets })
    let done = false, stage = 0, initial = false, metadata = false, hat = !modernHat, entityId
    const timer = setTimeout(() => finish(new Error('timeout ' + JSON.stringify({ stage, initial, metadata, hat }))), 20000)
    function finish(error) {
      if (done) return
      done = true; clearTimeout(timer); client.end()
      if (error) reject(new Error(version + ': ' + error.message))
      else resolve({ version, initialLayers: initial, toggledLayers: true, tabHat: modernHat ? 'off/on verified' : 'entity flags' })
    }
    function settings(parts) {
      client.write('settings', { locale: 'en_us', viewDistance: 2, chatFlags: 0, chatColors: true,
        skinParts: parts, mainHand: 1, enableTextFiltering: false, enableServerListing: true, particleStatus: 'all' })
    }
    client.on('error', finish)
    client.on('end', reason => { if (!done) finish(new Error('disconnected: ' + reason)) })
    client.on('kick_disconnect', data => finish(new Error(JSON.stringify(data))))
    client.on('packet', (data, meta) => {
      try {
        if (meta.state !== 'play') return
        if (meta.name === 'login') entityId = data.entityId
        if (meta.name === 'position') {
          if (data.teleportId !== undefined) client.write('teleport_confirm', { teleportId: data.teleportId })
          if (stage === 0) { stage = 1; metadata = false; hat = !modernHat; settings(0x3f) }
        }
        if (meta.name === 'entity_metadata' && data.entityId === entityId) {
          const key = pv === 47 ? 10 : pv >= 773 ? 16 : 17
          const parts = data.metadata.find(entry => entry.key === key)
          if (parts?.value === 127) initial = true
          if (stage > 0 && parts?.value === (stage === 1 ? 63 : 127)) metadata = true
        }
        if (meta.name === 'player_info' && modernHat && data.action.update_hat)
          if (data.data.some(entry => entry.showHat === (stage === 2))) hat = true
        if (stage && metadata && hat) {
          if (stage === 1) { stage = 2; metadata = false; hat = !modernHat; settings(0x7f) }
          else { assert.ok(initial); finish() }
        }
      } catch (error) { finish(error) }
    })
  })
}
;(async () => {
  let passed = 0
  for (let i = 0; i < versions.length; i++) {
    try { console.log('PASS', JSON.stringify(await probe(versions[i], i))); passed++ }
    catch (error) { console.error('FAIL', error.message); process.exitCode = 1 }
  }
  console.log(passed + '/' + versions.length + ' skin-layer versions passed')
})()
