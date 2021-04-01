const toggleKeycloak = () => {
    if (Alteryx.Gui.Manager.getDataItem('UseKeycloak').getValue()) {
        document.getElementById('keycloakServerURL').style.display = 'initial'
        document.getElementById('keycloakTokenEndpoint').style.display = 'initial'
        document.getElementById('keycloakRealm').style.display = 'initial'
        document.getElementById('clientID').style.display = 'initial'
    } else {
        document.getElementById('keycloakServerURL').style.display = 'none'
        document.getElementById('keycloakTokenEndpoint').style.display = 'none'
        document.getElementById('keycloakRealm').style.display = 'none'
        document.getElementById('clientID').style.display = 'none'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {
  toggleKeycloak()
}

Alteryx.Gui.AfterLoad = function (manager) {
    toggleKeycloak()
    Alteryx.Gui.Manager.getDataItem('UseKeycloak').registerPropertyListener('value', toggleKeycloak)
}
