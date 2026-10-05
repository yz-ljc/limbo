'use strict'
const mc = require('minecraft-protocol')
const assert = require('node:assert/strict')
const minecraftData = require('minecraft-data')
const host = process.env.LIMBO_HOST || '127.0.0.1'
const port = Number(process.env.LIMBO_PORT || 25566)
const expectedBrand = process.env.LIMBO_BRAND || 'Limbo'
const versions = process.argv.slice(2)
if (!versions.length) versions.push(...mc.supportedVersions.filter(v => v !== '1.7'),
  '1.9', '1.9.1', '1.9.2', '1.11', '1.12', '1.12.1', '1.13', '1.13.1',
  '1.14', '1.14.1', '1.14.2', '1.14.3', '1.15', '1.15.1', '1.16', '1.16.1',
  '1.16.2', '1.16.3', '1.17', '1.18')

async function probe(version, index) {
  return new Promise((resolve, reject) => {
    const seen = { login: 0, chunks: 0, position: 0, keepAlive: 0, brand: null, abilities: false, picked: false }
    // minecraft-data incorrectly aliases 1.9.1 to 1.9 (byte dimension).
    // 1.9.1 already uses the int dimension layout shared with 1.9.2.
    const wireVersion = version === '1.9.1' ? '1.9.2' : version
    const options = { host, port, username: 'Probe' + index, version: wireVersion, auth: 'offline', hideErrors: true }
    const client = mc.createClient(options)
    // Preserve requested handshake IDs even when the library reuses another version's schema.
    options.protocolVersion = minecraftData.versions.pc.find(v => v.minecraftVersion === version).version
    let done = false
    const timeout = setTimeout(() => finish(new Error('timeout: ' + JSON.stringify(seen))), 45000)
    function finish(error) {
      if (done) return
      done = true
      clearTimeout(timeout)
      client.end()
      if (error) reject(new Error(version + ': ' + error.message))
      else resolve({ version, protocol: options.protocolVersion, ...seen })
    }
    client.on('error', finish)
    client.on('end', reason => { if (!done) finish(new Error('disconnected: ' + reason)) })
    client.on('disconnect', data => finish(new Error(JSON.stringify(data))))
    client.on('kick_disconnect', data => finish(new Error(JSON.stringify(data))))
    client.on('packet', (data, meta) => {
      try {
        if (meta.state !== 'play') return
        if (meta.name === 'login') {
          seen.login++
          assert.equal(data.entityId, 1)
          // Modern versions nest world/gamemode fields.
          const gameMode = data.gameMode ?? data.worldState?.gamemode
          if (gameMode !== undefined) assert.ok(gameMode === 0 || gameMode === 'survival')
          if (typeof data.dimension === 'number') assert.equal(data.dimension, 1)
          if (typeof data.dimension === 'string') assert.equal(data.dimension, 'minecraft:the_end')
          if (data.dimension?.value?.effects) assert.equal(data.dimension.value.effects.value, 'minecraft:the_end')
          if (data.worldName !== undefined) assert.equal(data.worldName, 'minecraft:the_end')
          if (data.worldState !== undefined) assert.equal(data.worldState.name, 'minecraft:the_end')
        }
        if (meta.name === 'map_chunk') seen.chunks++
        if (meta.name === 'abilities') {
          assert.equal(data.flags, options.protocolVersion < 769 ? 9 : 1)
          seen.abilities = true
        }
        if (options.protocolVersion >= 769 && ['set_slot', 'set_player_inventory'].includes(meta.name)) {
          const item = data.item ?? data.contents
          assert.equal(item.itemCount, 1)
          assert.equal(item.itemId, minecraftData(version).itemsByName.cobblestone.id)
          seen.picked = true
        }
        if (meta.name === 'custom_payload' && ['MC|Brand', 'minecraft:brand'].includes(data.channel)) {
          let offset = 0
          let length = 0
          for (let shift = 0; shift < 35; shift += 7) {
            const value = data.data[offset++]
            length |= (value & 127) << shift
            if (!(value & 128)) break
          }
          assert.equal(data.data.length - offset, length)
          seen.brand = data.data.subarray(offset).toString('utf8')
          assert.equal(seen.brand, expectedBrand)
        }
        if (meta.name === 'position') {
          seen.position++
          assert.ok(Number.isFinite(data.x) && Number.isFinite(data.y) && Number.isFinite(data.z))
          if (data.teleportId !== undefined) client.write('teleport_confirm', { teleportId: data.teleportId })
          if (options.protocolVersion >= 769) client.write('pick_item_from_block', {
            position: { x: 0, y: 63, z: 0 }, includeData: false
          })
        }
        if (meta.name === 'keep_alive') seen.keepAlive++
        // Seeing a second heartbeat proves the server accepted the first response.
        if (seen.login && seen.chunks >= 25 && seen.position && seen.keepAlive >= 2 && seen.brand !== null
          && seen.abilities && (options.protocolVersion < 769 || seen.picked)) {
          setTimeout(() => finish(), 150)
        }
      } catch (e) { finish(e) }
    })
  })
}

;(async () => {
  const results = []
  for (let offset = 0; offset < versions.length; offset += 4) {
    const batch = await Promise.allSettled(versions.slice(offset, offset + 4).map((v, i) => probe(v, offset + i)))
    for (const result of batch) {
      if (result.status === 'fulfilled') {
        results.push(result.value)
        console.log('PASS', JSON.stringify(result.value))
      } else {
        process.exitCode = 1
        console.error('FAIL', result.reason.message)
      }
    }
  }
  console.log(results.length + '/' + versions.length + ' versions passed')
})()
