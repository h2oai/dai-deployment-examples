const displayStandardMliOptions = () => {
    if (Alteryx.Gui.Manager.getDataItem('IsTimeSeries').getValue()) {
        document.getElementById('datasetkey').style.display = 'none'
        document.getElementById('targetcolumn').style.display = 'none'
    } else {
        document.getElementById('datasetkey').style.display = 'initial'
        document.getElementById('targetcolumn').style.display = 'initial'
    }
}

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
    displayStandardMliOptions()
    toggleKeycloak()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayStandardMliOptions()
    toggleKeycloak()
    Alteryx.Gui.Manager.getDataItem('IsTimeSeries').registerPropertyListener('value', displayStandardMliOptions)
    Alteryx.Gui.Manager.getDataItem('UseKeycloak').registerPropertyListener('value', toggleKeycloak)
}